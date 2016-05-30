/*
 * Copyright 2002-2016 jamod & j2mod development teams
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghgande.j2mod.modbus.net;

import com.ghgande.j2mod.modbus.ModbusIOException;
import com.ghgande.j2mod.modbus.io.AbstractModbusTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class implementing a handler for incoming Modbus/TCP requests.
 *
 * @author Dieter Wimberger
 * @author Steve O'Hara (4energy)
 * @version 2.0 (March 2016)
 */
public class TCPConnectionHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TCPConnectionHandler.class);

    private TCPSlaveConnection connection;
    private AbstractModbusTransport transport;

    /**
     * Constructs a new <tt>TCPConnectionHandler</tt> instance.
     *
     * <p>
     * The connections will be handling using the <tt>ModbusCouple</tt> class
     * and a <tt>ProcessImage</tt> which provides the interface between the
     * slave implementation and the <tt>TCPSlaveConnection</tt>.
     *
     * @param con an incoming connection.
     */
    public TCPConnectionHandler(TCPSlaveConnection con) {
        setConnection(con);
    }

    /**
     * Sets a connection to be handled by this <tt>
     * TCPConnectionHandler</tt>.
     *
     * @param con a <tt>TCPSlaveConnection</tt>.
     */
    public void setConnection(TCPSlaveConnection con) {
        connection = con;
        transport = connection.getModbusTransport();
    }

    @Override
    public void run() {
        try {
            do {
                AbstractModbusListener.handleRequest(transport);
            } while (!Thread.currentThread().isInterrupted());
        }
        catch (ModbusIOException ex) {
            if (!ex.isEOF()) {
                logger.debug(ex.getMessage());
            }
        }
        finally {
            connection.close();
        }
    }
}