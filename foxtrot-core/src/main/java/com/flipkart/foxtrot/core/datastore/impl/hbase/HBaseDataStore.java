package com.flipkart.foxtrot.core.datastore.impl.hbase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.foxtrot.common.Document;
import com.flipkart.foxtrot.core.datastore.DataStore;
import com.flipkart.foxtrot.core.datastore.DataStoreException;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * User: Santanu Sinha (santanu.sinha@flipkart.com)
 * Date: 13/03/14
 * Time: 7:54 PM
 */
public class HbaseDataStore implements DataStore {
    private static final Logger logger = LoggerFactory.getLogger(HbaseDataStore.class.getSimpleName());

    private static final byte[] COLUMN_FAMILY = Bytes.toBytes("d");
    private static final byte[] DATA_FIELD_NAME = Bytes.toBytes("data");
    private static final byte[] TIMESTAMP_FIELD_NAME = Bytes.toBytes("timestamp");

    private final HbaseTableConnection tableWrapper;
    private final ObjectMapper mapper;

    public HbaseDataStore(HbaseTableConnection tableWrapper, ObjectMapper mapper) {
        this.tableWrapper = tableWrapper;
        this.mapper = mapper;
    }

    @Override
    public void save(final String table, Document document) throws DataStoreException {
        if (document == null || document.getData() == null || document.getId() == null) {
            throw new DataStoreException(DataStoreException.ErrorCode.STORE_INVALID_DOCUMENT, "Saving document error");
        }
        HTableInterface hTable = null;
        try {
            hTable = tableWrapper.getTable();
            hTable.put(getPutForDocument(table, document));
        } catch (Throwable t) {
            throw new DataStoreException(DataStoreException.ErrorCode.STORE_SINGLE_SAVE,
                    "Saving document error: " + t.getMessage(), t);
        } finally {
            if (null != hTable) {
                try {
                    hTable.close();
                } catch (IOException e) {
                    logger.error("Error closing table: ", e);
                }
            }
        }
    }

    @Override
    public void save(final String table, List<Document> documents) throws DataStoreException {
        if (documents == null) {
            throw new DataStoreException(DataStoreException.ErrorCode.STORE_INVALID_REQUEST, "Invalid Documents List");
        }
        HTableInterface hTable = null;
        try {
            hTable = tableWrapper.getTable();
            List<Put> puts = new Vector<Put>();
            for (Document document : documents) {
                if (document == null || document.getData() == null || document.getId() == null) {
                    throw new DataStoreException(DataStoreException.ErrorCode.STORE_INVALID_DOCUMENT,
                            "Saving document error");
                }
                puts.add(getPutForDocument(table, document));
            }
            hTable.put(puts);
        } catch (Throwable t) {
            throw new DataStoreException(DataStoreException.ErrorCode.STORE_MULTI_SAVE,
                    "Saving document error: " + t.getMessage(), t);
        } finally {
            if (null != hTable) {
                try {
                    hTable.close();
                } catch (IOException e) {
                    logger.error("Error closing table: ", e);
                }
            }
        }
    }

    @Override
    public Document get(final String table, String id) throws DataStoreException {
        HTableInterface hTable = null;
        try {
            Get get = new Get(Bytes.toBytes(id + ":" + table))
                    .addColumn(COLUMN_FAMILY, DATA_FIELD_NAME)
                    .addColumn(COLUMN_FAMILY, TIMESTAMP_FIELD_NAME)
                    .setMaxVersions(1);
            hTable = tableWrapper.getTable();
            Result getResult = hTable.get(get);
            if (!getResult.isEmpty()) {
                byte[] data = getResult.getValue(COLUMN_FAMILY, DATA_FIELD_NAME);
                byte[] timestamp = getResult.getValue(COLUMN_FAMILY, TIMESTAMP_FIELD_NAME);
                long time = Bytes.toLong(timestamp);
                return new Document(id, time, mapper.readTree(data));
            } else {
                throw new DataStoreException(DataStoreException.ErrorCode.STORE_NO_DATA_FOUND_FOR_ID,
                        String.format("No data found for ID: %s", id));
            }
        } catch (Throwable t) {
            throw new DataStoreException(DataStoreException.ErrorCode.STORE_SINGLE_GET,
                    t.getMessage(), t);
        } finally {
            if (null != hTable) {
                try {
                    hTable.close();
                } catch (IOException e) {
                    logger.error("Error closing table: ", e);
                }
            }
        }
    }

    @Override
    public List<Document> get(final String table, List<String> ids) throws DataStoreException {
        if (ids == null) {
            throw new DataStoreException(DataStoreException.ErrorCode.STORE_INVALID_REQUEST, "Invalid Request IDs");
        }

        HTableInterface hTable = null;
        try {
            List<Get> gets = new ArrayList<Get>(ids.size());
            for (String id : ids) {
                Get get = new Get(Bytes.toBytes(id + ":" + table))
                        .addColumn(COLUMN_FAMILY, DATA_FIELD_NAME)
                        .addColumn(COLUMN_FAMILY, TIMESTAMP_FIELD_NAME)
                        .setMaxVersions(1);
                gets.add(get);
            }
            hTable = tableWrapper.getTable();
            Result[] getResults = hTable.get(gets);
            List<Document> results = new ArrayList<Document>(ids.size());
            for (int index = 0; index < getResults.length; index++) {
                Result getResult = getResults[index];
                if (!getResult.isEmpty()) {
                    byte[] data = getResult.getValue(COLUMN_FAMILY, DATA_FIELD_NAME);
                    byte[] timestamp = getResult.getValue(COLUMN_FAMILY, TIMESTAMP_FIELD_NAME);
                    long time = Bytes.toLong(timestamp);
                    results.add(new Document(Bytes.toString(getResult.getRow()).split(":")[0],
                            time, mapper.readTree(data)));
                } else {
                    throw new DataStoreException(DataStoreException.ErrorCode.STORE_NO_DATA_FOUND_FOR_IDS,
                            String.format("No data found for ID: %s", ids.get(index)));
                }
            }
            return results;
        } catch (Throwable t) {
            throw new DataStoreException(DataStoreException.ErrorCode.STORE_MULTI_GET,
                    t.getMessage(), t);
        } finally {
            if (null != hTable) {
                try {
                    hTable.close();
                } catch (IOException e) {
                    logger.error("Error closing table: ", e);
                }
            }
        }
    }

    public Put getPutForDocument(final String table, Document document) throws Throwable {
        return new Put(Bytes.toBytes(document.getId() + ":" + table))
                .add(COLUMN_FAMILY, DATA_FIELD_NAME, mapper.writeValueAsBytes(document.getData()))
                .add(COLUMN_FAMILY, TIMESTAMP_FIELD_NAME, Bytes.toBytes(document.getTimestamp()));
    }
}