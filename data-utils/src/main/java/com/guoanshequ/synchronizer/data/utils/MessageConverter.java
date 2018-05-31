package com.guoanshequ.synchronizer.data.utils;

import com.alibaba.otter.canal.protocol.CanalEntry.ColumnOrBuilder;
import com.guoanshequ.synchronizer.data.model.CanalBean.RowData.ColumnEntry;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.util.List;
import java.util.Map;

public final class MessageConverter {

    private final static Logger logger = LoggerFactory.getLogger(MessageConverter.class);

    /**
     * 打印并进行封装，内部有mysql特殊类型的转换过程
     *
     * @param columns 列参数
     * @return 返回值
     */
    public static Map<String, ColumnEntry> createColumnMap(List<? extends ColumnOrBuilder> columns) {
        Map<String, ColumnEntry> columnEntryMap = new ConcurrentReferenceHashMap<>();

        for (ColumnOrBuilder column : columns) {

            ColumnEntry columnEntry;

            StringBuilder builder = new StringBuilder();

            //由于canalEntry 自动将enum类型转换成了int类型，
            // 所以在这进行将enum类型转换成varchar类型 sql type为12，将其值映射成字符串
            if (column.getMysqlType().startsWith("enum")) {
                String value = "";
                //将字符串截取，然后分割成数组，根据索引值获取到enum的指定值
                String[] arr = column.getMysqlType().substring(5, column.getMysqlType().length() - 1).split(",");
                //判空
                if (StringUtils.isNotEmpty(column.getValue())) {
                    //如果 column.getValue() 是 0 ，说明这个字段为空，赋值空字符串
                    if (!"0".equals(column.getValue())) {
                        value = arr[Integer.valueOf(column.getValue()) - 1].replace("'", "");
                    }
                }
                builder.append("Column Name:").append(column.getName()).append(", type:").append("12").append(", isKey:").append(column.getIsKey()).append(", updated:").append(column.getUpdated()).append(", isNull:").append(column.getIsNull()).append(", value:").append(value);
                logger.debug(builder.toString());
                columnEntry = new ColumnEntry(column.getName(), 12, column.getIsKey(), column.getUpdated(), column.getIsNull(), value);
            } else if ("datetime".equals(column.getMysqlType())) {
                //如果类型为datetime类型，需要转换成string类型
                builder.append("Column Name:").append(column.getName()).append(", type:").append("12").append(", isKey:").append(column.getIsKey()).append(", updated:").append(column.getUpdated()).append(", isNull:").append(column.getIsNull()).append(", value:").append(column.getValue());
                logger.debug(builder.toString());
                columnEntry = new ColumnEntry(column.getName(), 12, column.getIsKey(), column.getUpdated(), column.getIsNull(), column.getValue());
            } else {
                //正常字段不用特殊处理
                builder.append("Column Name:").append(column.getName()).append(", type:").append(column.getSqlType()).append(", isKey:").append(column.getIsKey()).append(", updated:").append(column.getUpdated()).append(", isNull:").append(column.getIsNull()).append(", value:").append(column.getValue());
                logger.debug(builder.toString());
                columnEntry = new ColumnEntry(column.getName(), column.getSqlType(), column.getIsKey(), column.getUpdated(), column.getIsNull(), column.getValue());
            }
            columnEntryMap.put(column.getName(), columnEntry);
        }
        return columnEntryMap;
    }
}
