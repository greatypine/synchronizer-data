package com.guoanshequ.synchronizer.data.sender.kudu;

import com.guoanshequ.synchronizer.data.model.CanalBean;
import com.guoanshequ.synchronizer.data.model.CanalBean.RowData.ColumnEntry;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.kudu.ColumnSchema;
import org.apache.kudu.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.alibaba.otter.canal.protocol.CanalEntry.EventType.*;

public class KuduTableOperation {

    private static final Logger logger = LoggerFactory.getLogger(KuduTableOperation.class);

    public static void syncTable(String tableName, KuduClient client, List<CanalBean> canalBeanList) {

        KuduSession kuduSession = null;
        try {
            String impalaTable = StringUtils.join(new String[]{"impala::", tableName});

            KuduTable kuduTable = client.openTable(impalaTable);
            kuduSession = client.newSession();

            for (CanalBean canalBean : canalBeanList) {
                Operation operation = createOperation(kuduTable, canalBean);
                if (operation != null) {
                    kuduSession.apply(operation);
                }
            }
        } catch (KuduException e) {
            logger.error("data operation failed. cause: {}, message: {}.", e.getCause(), e.getMessage());
        } finally {
            try {
                kuduSession.close();
            } catch (KuduException e) {
                logger.error("close kudu session failed. cause: {}, message: {}.", e.getCause(), e.getMessage());
            }
        }
    }

    private static Operation createOperation(KuduTable kuduTable, CanalBean canalBean) {
        Operation operation = null;
        Map<String, ColumnEntry> columnEntryMap = null;
        if (canalBean.getEventType() == INSERT.getNumber()) {
            operation = kuduTable.newInsert();
            columnEntryMap = canalBean.getRowData().getAfterColumns();
        } else if (canalBean.getEventType() == DELETE.getNumber()) {
            operation = kuduTable.newDelete();
            columnEntryMap = canalBean.getRowData().getBeforeColumns();
        } else if (canalBean.getEventType() == UPDATE.getNumber()) {
            operation = kuduTable.newUpdate();
            columnEntryMap = canalBean.getRowData().getAfterColumns();
        }

        setColumns(kuduTable, operation, columnEntryMap);

        return operation;
    }

    private static void setColumns(KuduTable kuduTable, Operation operation, Map<String, ColumnEntry> columnEntryMap) {

        if (operation!=null) {
            PartialRow partialRow = operation.getRow();
            List<ColumnSchema> columnSchemaList = kuduTable.getSchema().getColumns();
            for (ColumnSchema columnSchema : columnSchemaList) {
                String columnName = columnSchema.getName();
                String columnValue = columnEntryMap.get(columnName).getValue();
                logger.debug("column {}'s type: {}, value : {}", columnName, JDBCType.valueOf(columnEntryMap.get(columnName).getType()).getName(), columnValue);
                switch (columnSchema.getType()) {
                    case INT8:
                        partialRow.addByte(columnName, Byte.parseByte(columnValue));
                        break;
                    case INT16:
                        partialRow.addShort(columnName, Short.parseShort(columnValue));
                        break;
                    case INT32:
                        partialRow.addInt(columnName, Integer.parseInt(columnValue));
                        break;
                    case INT64:
                        partialRow.addLong(columnName, Long.parseLong(columnValue));
                        break;
                    case BINARY:
                        partialRow.addBinary(columnName, Bytes.fromUnsignedInt(Long.parseLong(columnValue, 2)));
                        break;
                    case STRING:
                        partialRow.addString(columnName, columnValue);
                        break;
                    case BOOL:
                        partialRow.addBoolean(columnName,  Boolean.parseBoolean(columnValue));
                        break;
                    case FLOAT:
                        partialRow.addFloat(columnName, Float.parseFloat(columnValue));
                        break;
                    case DOUBLE:
                        partialRow.addDouble(columnName, Double.parseDouble(columnValue));
                        break;
                    case UNIXTIME_MICROS:
                        try {
                            Date date = DateUtils.parseDate(columnValue, new String[]{"yyyy-MM-dd HH:mm:ss"});
                            partialRow.addLong(columnName, DateUtils.addHours(date, 8).getTime() * 1000);
                        } catch (ParseException e) {
                            logger.error("date format failed. cause: {}, message: {}.", e.getCause(), e.getMessage());
                        }
                        break;
                    case DECIMAL:
                        partialRow.addDecimal(columnName, BigDecimal.valueOf(Double.parseDouble(columnValue)));
                        break;
                }
            }
        }
    }
}
