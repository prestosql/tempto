/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.trino.tempto.internal.configuration;

import io.trino.tempto.configuration.Configuration;
import io.trino.tempto.configuration.KeyUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.trino.tempto.configuration.KeyUtils.joinKey;
import static io.trino.tempto.internal.configuration.EmptyConfiguration.emptyConfiguration;

/**
 * <p>
 * This class constructs and stores a map based configuration using nested maps.
 * </p>
 * <p>
 * For leafs - value is stored in map.
 * For non-leaf - nested map is stored.
 * </p>
 * <p>
 * Outer map is responsible for first part in all keys stored in configuration.
 * Maps stored at first internal level are responsible for second part in stored keys and so on.
 * </p>
 * <p>
 * For example, for a configuration storing these hierarchical entries:
 * a.b.c = 3
 * a.x = 10
 * b.c = 15
 * </p>
 * <p>
 * the following map structure would be created
 * </p>
 * <pre>
 * {
 * a : {
 *  b : {
 *   c : 3
 *  },
 *  x : 10
 *  },
 * b : {
 *  c : 15
 *  }
 * }
 * </pre>
 */
public class MapConfiguration
        extends AbstractConfiguration
{
    private final Map<String, Object> map;

    public MapConfiguration(Map<String, Object> map)
    {
        this.map = map;
    }

    @Override
    public Optional<Object> get(String key)
    {
        Optional<Object> object = getObject(key);
        if (object.isPresent() && object.get() instanceof Map) {
            return Optional.empty();
        }
        return object;
    }

    private Optional<Object> getObject(String key)
    {
        if (map.containsKey(key)) {
            return Optional.of(map.get(key));
        }

        List<String> keyParts = KeyUtils.splitKey(key);
        for (int prefixLength = 1; prefixLength < keyParts.size(); prefixLength++) {
            String prefix = joinKey(keyParts.subList(0, prefixLength));
            if (map.get(prefix) instanceof Map) {
                String remainingKey = joinKey(keyParts.subList(prefixLength, keyParts.size()));
                return new MapConfiguration((Map<String, Object>) map.get(prefix)).getObject(remainingKey);
            }
        }
        return Optional.empty();
    }

    @Override
    public Set<String> listKeys()
    {
        Set<String> acc = new HashSet<>();
        listKeys(map, null, acc);
        return acc;
    }

    @Override
    public Set<String> listPrefixes()
    {
        return map.keySet();
    }

    private void listKeys(Map<String, Object> map, String currentPrefix, Set<String> acc)
    {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String currentKey = joinKey(currentPrefix, entry.getKey());
            if (entry.getValue() instanceof Map) {
                listKeys((Map<String, Object>) entry.getValue(), currentKey, acc);
            }
            else {
                acc.add(currentKey);
            }
        }
    }

    @Override
    public Configuration getSubconfiguration(String keyPrefix)
    {
        Optional<Object> object = getObject(keyPrefix);
        if (object.isPresent() && object.get() instanceof Map) {
            return new MapConfiguration((Map<String, Object>) object.get());
        }
        else {
            return emptyConfiguration();
        }
    }
}
