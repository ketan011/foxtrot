package com.flipkart.foxtrot.common;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.flipkart.foxtrot.common.group.GroupResponse;
import com.flipkart.foxtrot.common.query.QueryResponse;

/**
 * User: Santanu Sinha (santanu.sinha@flipkart.com)
 * Date: 25/03/14
 * Time: 9:17 PM
 */
@JsonTypeInfo(use= JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "opcode")
@JsonSubTypes({
        @JsonSubTypes.Type(value = QueryResponse.class, name= "query"),
        @JsonSubTypes.Type(value = GroupResponse.class, name= "group")
})

public interface ActionResponse {
}