/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.qpid.server.model.preferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PreferenceTestHelper
{
    public static Map<String, Object> createPreferenceAttributes(UUID associatedObjectId,
                                                                 UUID id,
                                                                 String type,
                                                                 String name,
                                                                 String description,
                                                                 String owner,
                                                                 Set<String> visibilitySet,
                                                                 Map<String, Object> preferenceValueAttributes)
    {
        Map<String, Object> preferenceAttributes = new HashMap<>();
        preferenceAttributes.put(Preference.ASSOCIATED_OBJECT_ATTRIBUTE,
                                 associatedObjectId == null ? null : associatedObjectId.toString());
        preferenceAttributes.put(Preference.ID_ATTRIBUTE, id);
        preferenceAttributes.put(Preference.TYPE_ATTRIBUTE, type);
        preferenceAttributes.put(Preference.NAME_ATTRIBUTE, name);
        preferenceAttributes.put(Preference.DESCRIPTION_ATTRIBUTE, description);
        preferenceAttributes.put(Preference.OWNER_ATTRIBUTE, owner);
        preferenceAttributes.put(Preference.VISIBILITY_LIST_ATTRIBUTE, visibilitySet);
        preferenceAttributes.put(Preference.VALUE_ATTRIBUTE, preferenceValueAttributes);
        return preferenceAttributes;
    }
}
