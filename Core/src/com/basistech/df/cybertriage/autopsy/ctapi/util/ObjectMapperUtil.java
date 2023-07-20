/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp. It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2023 Basis Technology Corp. All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
package com.basistech.df.cybertriage.autopsy.ctapi.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Creates default ObjectMapper
 */
public class ObjectMapperUtil {

    private static final ObjectMapperUtil instance = new ObjectMapperUtil();

    public static ObjectMapperUtil getInstance() {
        return instance;
    }

    private ObjectMapperUtil() {

    }

    public ObjectMapper getDefaultObjectMapper() {
        ObjectMapper defaultMapper = new ObjectMapper();
        defaultMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        defaultMapper.registerModule(new JavaTimeModule());
        return defaultMapper;
    }

}
