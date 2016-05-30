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
package com.ghgande.j2mod.modbus.msg;

import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.ModbusCoupler;
import com.ghgande.j2mod.modbus.msg.ReadFileRecordResponse.RecordResponse;
import com.ghgande.j2mod.modbus.procimg.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Class implementing a <tt>Read File Record</tt> request.
 *
 * @author Julie Haugh (jfh@ghgande.com)
 * @author Steve O'Hara (4energy)
 * @version 2.0 (March 2016)
 */
public final class ReadFileRecordRequest extends ModbusRequest {
    private RecordRequest[] records;

    /**
     * Constructs a new <tt>Read File Record</tt> request instance.
     */
    public ReadFileRecordRequest() {
        super();

        setFunctionCode(Modbus.READ_FILE_RECORD);

        // Request size byte is all that is required.
        setDataLength(1);
    }

    /**
     * getRequestSize -- return the total request size. This is useful for
     * determining if a new record can be added.
     *
     * @return size in bytes of response.
     */
    public int getRequestSize() {
        if (records == null) {
            return 1;
        }

        int size = 1;
        for (RecordRequest record : records) {
            size += record.getRequestSize();
        }

        return size;
    }

    /**
     * getRequestCount -- return the number of record requests in this message.
     */
    public int getRequestCount() {
        if (records == null) {
            return 0;
        }

        return records.length;
    }

    /**
     * getRecord -- return the record request indicated by the reference
     */
    public RecordRequest getRecord(int index) {
        return records[index];
    }

    /**
     * addRequest -- add a new record request.
     */
    public void addRequest(RecordRequest request) {
        if (request.getRequestSize() + getRequestSize() > 248) {
            throw new IllegalArgumentException();
        }

        if (records == null) {
            records = new RecordRequest[1];
        }
        else {
            RecordRequest old[] = records;
            records = new RecordRequest[old.length + 1];

            System.arraycopy(old, 0, records, 0, old.length);
        }
        records[records.length - 1] = request;

        setDataLength(getRequestSize());
    }

    /**
     * getResponse -- get an empty response for this message.
     */
    public ModbusResponse getResponse() {
        ReadFileRecordResponse response;

        response = new ReadFileRecordResponse();

        // Copy any header data from the request.
        response.setHeadless(isHeadless());
        if (!isHeadless()) {
            response.setTransactionID(getTransactionID());
            response.setProtocolID(getProtocolID());
        }

        // Copy the unit ID and function code.
        response.setUnitID(getUnitID());
        response.setFunctionCode(getFunctionCode());

        return response;
    }

    /**
     * The ModbusCoupler doesn't have a means of reporting the slave state or ID
     * information.
     */
    public ModbusResponse createResponse() {
        ReadFileRecordResponse response;
        response = (ReadFileRecordResponse)getResponse();

        // Get the process image.
        ProcessImage procimg = ModbusCoupler.getReference().getProcessImage(getUnitID());

        // There is a list of requests to be resolved.
        try {
            for (int i = 0; i < getRequestCount(); i++) {
                RecordRequest recordRequest = getRecord(i);
                if (recordRequest.getFileNumber() < 0
                        || recordRequest.getFileNumber() >= procimg.getFileCount()) {
                    return createExceptionResponse(Modbus.ILLEGAL_ADDRESS_EXCEPTION);
                }

                File file = procimg.getFileByNumber(recordRequest.getFileNumber());

                if (recordRequest.getRecordNumber() < 0
                        || recordRequest.getRecordNumber() >= file.getRecordCount()) {
                    return createExceptionResponse(Modbus.ILLEGAL_ADDRESS_EXCEPTION);
                }

                Record record = file.getRecord(recordRequest.getRecordNumber());
                int registers = recordRequest.getWordCount();
                if (record == null && registers != 0) {
                    return createExceptionResponse(Modbus.ILLEGAL_ADDRESS_EXCEPTION);
                }

                short data[] = new short[registers];
                for (int j = 0; j < registers; j++) {
                    Register register = record.getRegister(j);
                    if (register == null) {
                        return createExceptionResponse(Modbus.ILLEGAL_ADDRESS_EXCEPTION);
                    }

                    data[j] = register.toShort();
                }
                RecordResponse recordResponse = new RecordResponse(data);
                response.addResponse(recordResponse);
            }
        }
        catch (IllegalAddressException e) {
            return createExceptionResponse(Modbus.ILLEGAL_ADDRESS_EXCEPTION);
        }
        return response;
    }

    /**
     * writeData -- output this Modbus message to dout.
     */
    public void writeData(DataOutput dout) throws IOException {
        dout.write(getMessage());
    }

    /**
     * readData -- read all the data for this request.
     */
    public void readData(DataInput din) throws IOException {
        int byteCount = din.readUnsignedByte();

        int recordCount = byteCount / 7;
        records = new RecordRequest[recordCount];

        for (int i = 0; i < recordCount; i++) {
            if (din.readByte() != 6) {
                throw new IOException();
            }

            int file = din.readUnsignedShort();
            int record = din.readUnsignedShort();
            if (record < 0 || record >= 10000) {
                throw new IOException();
            }

            int count = din.readUnsignedShort();

            records[i] = new RecordRequest(file, record, count);
        }
    }

    /**
     * getMessage -- return the PDU message.
     */
    public byte[] getMessage() {
        byte request[] = new byte[1 + 7 * records.length];

        int offset = 0;
        request[offset++] = (byte)(request.length - 1);

        for (RecordRequest record : records) {
            record.getRequest(request, offset);
            offset += 7;
        }
        return request;
    }

    public static class RecordRequest {
        private int fileNumber;
        private int recordNumber;
        private int wordCount;

        public RecordRequest(int file, int record, int count) {
            fileNumber = file;
            recordNumber = record;
            wordCount = count;
        }

        public int getFileNumber() {
            return fileNumber;
        }

        public int getRecordNumber() {
            return recordNumber;
        }

        public int getWordCount() {
            return wordCount;
        }

        /**
         * getRequestSize -- return the size of the response in bytes.
         */
        public int getRequestSize() {
            return 7 + wordCount * 2;
        }

        public void getRequest(byte[] request, int offset) {
            request[offset] = 6;
            request[offset + 1] = (byte)(fileNumber >> 8);
            request[offset + 2] = (byte)(fileNumber & 0xFF);
            request[offset + 3] = (byte)(recordNumber >> 8);
            request[offset + 4] = (byte)(recordNumber & 0xFF);
            request[offset + 5] = (byte)(wordCount >> 8);
            request[offset + 6] = (byte)(wordCount & 0xFF);
        }

        public byte[] getRequest() {
            byte[] request = new byte[7];

            getRequest(request, 0);

            return request;
        }
    }
}