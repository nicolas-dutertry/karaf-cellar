/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.cellar.core.command;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Basic command store.
 */
public class BasicCommandStore implements CommandStore {

    private ConcurrentMap<String, Command> pending = new ConcurrentHashMap<String, Command>();

    /**
     * Get the pending commands in the store.
     *
     * @return the map of pending commands.
     */
    @Override
    public ConcurrentMap<String, Command> getPending() {
        return pending;
    }

    /**
     * Set the pending commands in the store.
     *
     * @param pending the map of pending commands.
     */
    @Override
    public void setPending(ConcurrentMap<String, Command> pending) {
        this.pending = pending;
    }

}
