package prism.akash.container.sqlEngine;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import org.apache.commons.lang.StringEscapeUtils;
import prism.akash.container.BaseData;
import prism.akash.container.sqlEngine.engineEnum.*;

import java.io.Serializable;
import java.util.LinkedHashMap;

public class sqlEngine implements Serializable {

    private static final long serialVersionUID = 1L;

    BaseData engine = null;
    boolean isGroup = false;

    public sqlEngine() {
        engine = new BaseData();
    }


    //TODO : 查询相关    ↓↓↓↓↓↓↓↓↓

    private sqlEngine queryType(queryType queryType) {
        engine.put("queryType", queryType.getQueryType());
        return this;
    }

    private sqlEngine queryConditionType(conditionType conditionType) {
        engine.put("conditionType", conditionType.getconditionType());
        return this;
    }

    private sqlEngine queryKey(String key) {
        engine.put("queryKey", StringEscapeUtils.escapeSql(key.toString()));
        return this;
    }

    private sqlEngine queryTable(String table) {
        engine.put("queryTable", StringEscapeUtils.escapeSql(table.toString()));
        return this;
    }

    private sqlEngine queryValue(String value) {
        engine.put("queryValue", "params_" + StringEscapeUtils.escapeSql(value.toString()));
        return this;
    }


    public sqlEngine caseBuild(String caseTable, String caseColumn, String caseAlias) {
        engine.put("caseTable", caseTable);
        engine.put("caseColumn", caseColumn);
        engine.put("caseAlias", caseAlias == null ? caseTable + "_" + caseColumn : caseAlias);
        return this;
    }

    public sqlEngine caseWhenQuery(queryType whenQuery, String whenTable, String whenColumn, conditionType whenCondition, String whenValue) {
        //TODO: 调用queryBuild获取筛选条件
        this.queryBuild(whenQuery, whenTable, whenColumn, whenCondition, whenValue);
        engine.put("caseWhenQuery", engine.get("caseWhenQuery") == null ? engine.get("query") : engine.get("caseWhenQuery") + whenQuery.getQueryType() + engine.get("query"));
        //将生产好的查询语句转存后清空
        engine.remove("query");
        return this;
    }

    public sqlEngine caseThen(String thenValue) {
        StringBuffer caseThen = new StringBuffer(" WHEN ");
        caseThen.append(engine.get("caseWhenQuery")).append(" THEN '").append(thenValue).append("'");
        engine.put("caseQuery", engine.get("caseQuery") == null ? caseThen : (engine.get("caseQuery").toString() + caseThen));
        //TODO：清空
        engine.remove("caseWhenQuery");
        return this;
    }

    public sqlEngine caseFin(String elseValue) {
        StringBuffer caseFin = new StringBuffer(" CASE ");
        caseFin.append(engine.get("caseQuery"))
                .append(" ELSE '").append(elseValue)
                .append("' END AS ").append(engine.get("caseAlias"));
//                .append(engine.get("caseTable")).append(".").append(engine.get("caseColumn"))
//                .append(" ")
        engine.put("caseFin", caseFin);
        //TODO：清空
        engine.remove("caseQuery");
        engine.remove("caseTable");
        engine.remove("caseColumn");
        engine.remove("caseAlias");
        return this;
    }

    /**
     * 查询语句生成
     *
     * @param selType true  -> where 使用
     *                false -> join where 使用
     * @return
     */
    private sqlEngine queryFin(boolean selType) {
        //连表或非连表查询（join where / where）
        String queryName = selType ? this.isGroup ? "groupQuery" : "query" : "joinQuery";

        StringBuffer query = new StringBuffer();

        //TODO : 若queryType未填写,则默认使用and作为连接条件
        String queryType = engine.get("queryType") == null ? "and" : engine.getString("queryType");
        //TODO : 如果当前引擎查询语句未生成,则忽略queryType指向
        if (engine.get(queryName) == null) {
            query.append(" ").append(queryType.contains("Merge") ? " (" : "");
        } else {
            query.append(" ").append(queryType.contains("Merge") ? queryType.contains("and") ? " and (" : " or (" : queryType).append(" ");
        }

        if (engine.get("queryTable") != null) {
            query.append(engine.get("queryTable")).append(".");
        }
        query.append(engine.get("queryKey"));
        //TODO : 根据当前筛选条件对数据进行格式重组
        String conditionType = engine.get("conditionType") == null ? "" : engine.getString("conditionType");
        boolean executeValue = false;
        if (conditionType.equals("")) {
            query.append(" = ");
        } else {
            query.append(conditionType);
            executeValue = conditionType.contains("LIKE") || conditionType.contains("IN") || conditionType.contains("NULL") || conditionType.contains("BET");
        }

        String queryValue = engine.get("queryValue") == null ? "" : engine.getString("queryValue");
        if (executeValue) {
            if (conditionType.equals(" IN ") || conditionType.equals(" NOT IN ")) {
                query.append(" (");
                for (String iv : queryValue.split(",")) {
                    if (!iv.trim().equals("") || iv != null)
                        query.append("'").append(iv).append("',");
                }
                query.deleteCharAt(query.length() - 1).append(")");
            } else if (conditionType.contains("BET")) {
                query.append("'").append(queryValue.split(",")[0]).append("'");
                query.append(" AND ");
                query.append("'").append(queryValue.split(",")[1]).append("'");
            } else if (conditionType.equals(" LIKE ")) {
                query.append(" '%").append(engine.get("queryValue")).append("%' ");
            } else if (conditionType.equals(" LIKE BINARY ")) {
                query.append(" CONCAT ('%',UPPER('").append(engine.get("queryValue")).append("','%')");
            }
        } else {
            //TODO: 当前是否嵌套了子查询

            if (engine.get("child") == null) {
                query.append(" '").append(engine.get("queryValue")).append("' ");
            } else {
                query.append(engine.get("queryValue"));
            }
        }

        //TODO : 判断本次组合查询是否已结束
        if (queryType.contains("End")) {
            query.append(" ) ");
        }

        engine.remove("queryType");
        engine.remove("conditionType");
        engine.remove("queryKey");
        engine.remove("queryValue");

        //TODO : 将生成的查询语句保存在引擎对象
        engine.put(queryName, (engine.get(queryName) == null ? " " : engine.getString(queryName)) + " " + query);
        return this;
    }

    /**
     * 指定子表
     *
     * @param joinTable 子表的表名
     * @return
     */
    private sqlEngine join(String joinTable, String joinAlias) {
        engine.put("joinTable", joinTable);
        engine.put("joinTableAlias", joinAlias);
        return this;
    }

    /**
     * 指定主子表关系
     *
     * @param joinType 关系类型枚举
     * @return
     */
    private sqlEngine joinType(joinType joinType) {
        engine.put("joinType", joinType.getJoinType());
        return this;
    }

    /**
     * 分组条件构建
     *
     * @param groupTable
     * @param groupColumns
     * @return
     */
    private sqlEngine groupBy(String groupTable, String groupColumns) {
        StringBuffer groupBy = new StringBuffer(",");

        if (groupColumns.length() > 0) {
            for (String col : groupColumns.split(",")) {
                groupBy.append(groupTable).append(".").append(col).append(",");
            }
        }

        groupBy = groupBy.deleteCharAt(0).deleteCharAt(groupBy.length() - 1);
        if (engine.get("groupBy") == null) {
            engine.put("groupBy", groupBy);
        } else {
            engine.put("groupBy", engine.getString("groupBy") + groupBy);
        }

        engine.put("groupBy", groupBy);
        return this;
    }


    /**
     * 生成Having需要的Key键
     *
     * @param groupType
     * @param queryType
     * @param groupTable
     * @param groupColumn
     * @return
     */
    private String getHavingKey(groupType groupType, queryType queryType, String groupTable, String groupColumn) {
        StringBuffer key = new StringBuffer(groupTable).append(".").append(groupColumn);
        if (!groupType.getgroupType().equals("DEF")) {
            key = key.insert(0, groupType.getgroupType() + "(").append(" )");
        }
        return key.toString();
    }

    /**
     * 指定需要查询的数据字段，以表为单位进行设定,若columns为空则视为查询当前表全部
     *
     * @param appointTable
     * @param appointColumns 字段以逗号隔开，如需指定别名可按照   原列名#列别名 进行设置
     * @return
     */
    public sqlEngine appointColumn(String appointTable, String appointColumns) {
        StringBuffer appoint = new StringBuffer(",");

        //TODO: 对传入数据进行处理优化
        if (appointColumns.length() < 1) {
            appoint.append(appointTable).append(".*");
            appoint = appoint.deleteCharAt(0);
        } else {
            for (String col : appointColumns.split(",")) {
                appoint.append(appointTable).append(".");
                if (col.contains("#")) {
                    appoint.append(col.split("#")[0]).append(" AS ").append(col.split("#")[1]).append(",");
                } else {
                    appoint.append(col).append(",");
                }
            }
            appoint = appoint.deleteCharAt(0).deleteCharAt(appoint.length() - 1);
        }

        if (engine.get("appointColumn") == null) {
            engine.put("appointColumn", appoint);
        } else {
            engine.put("appointColumn", engine.get("appointColumn").toString() + appoint);
        }
        return this;
    }


    /**
     * 分组条件构造器
     *
     * @param groupTable
     * @param groupColumns
     * @return
     */
    public sqlEngine groupBuild(String groupTable, String groupColumns) {
        this.isGroup = true;
        return this.groupBy(groupTable, groupColumns);
    }

    /**
     * 分组查询字段构造器
     *
     * @param groupType
     * @param groupTable
     * @param groupColumns
     * @return
     */
    public sqlEngine groupColumn(groupType groupType, String groupTable, String groupColumns) {
        StringBuffer groupColumn = new StringBuffer(",");

        if (groupColumns.length() > 0) {
            for (String col : groupColumns.split(",")) {
                String alias = "";
                if (col.contains("#")) {
                    alias = col.split("#")[1];
                }
                String key = alias.equals("") ? col : col.split("#")[0];
                //TODO：判断是否需要执行函数处理
                if (groupType.getgroupType().equals("DEF")) {
                    groupColumn.append(groupTable).append(".").append(key);
                } else {
                    groupColumn.append(" ").append(groupType.getgroupType()).append("(").append(groupTable).append(".").append(key).append(")");
                }
                //TODO: 增加别名
                if (!alias.equals("")) {
                    groupColumn.append(" AS ").append(alias);
                }
                groupColumn.append(" ,");
            }
            groupColumn = groupColumn.deleteCharAt(0).deleteCharAt(groupColumn.length() - 1);
        }


        if (engine.get("groupColumn") == null) {
            engine.put("groupColumn", groupColumn);
        } else {
            engine.put("groupColumn", engine.get("groupColumn") + "," + groupColumn);
        }

//        engine.put("groupColumn", engine.get("groupColumn") + ","+ groupColumn);
        return this;
    }


    /**
     * Having条件构造器
     *
     * @param groupType
     * @param queryType
     * @param groupTable
     * @param groupColumn
     * @param conditionType
     * @param value
     * @return
     */
    public sqlEngine groupHaving(groupType groupType, queryType queryType, String groupTable, String groupColumn, conditionType conditionType, String value) {
        this.isGroup = true;
        return this.queryType(queryType).queryKey(getHavingKey(groupType, queryType, groupTable, groupColumn)).queryConditionType(conditionType).queryValue(value).queryFin(true);
    }


    /**
     * 嵌套Having条件构造器
     *
     * @param groupType
     * @param queryType
     * @param groupTable
     * @param groupColumn
     * @param conditionType
     * @param child
     * @return
     */
    public sqlEngine groupHavingChild(groupType groupType, queryType queryType, String groupTable, String groupColumn, conditionType conditionType, sqlEngine child) {
        engine.put("child", "child");
        this.isGroup = true;
        return this.queryType(queryType).queryKey(getHavingKey(groupType, queryType, groupTable, groupColumn)).queryConditionType(conditionType).queryValue("(" + child.engine.get("select") + ")").queryFin(true);
    }

    /**
     * 查询条件构造器
     *
     * @param queryType
     * @param key
     * @param conditionType
     * @param value
     * @return
     */
    public sqlEngine queryBuild(queryType queryType, String table, String key, conditionType conditionType, String value) {
        this.isGroup = false;
        return this.queryType(queryType).queryTable(table).queryKey(key).queryConditionType(conditionType).queryValue(value).queryFin(true);
    }

    /**
     * 嵌套子查询条件构造器
     *
     * @param queryType
     * @param table
     * @param key
     * @param conditionType
     * @param child
     * @return
     */
    public sqlEngine queryChild(queryType queryType, String table, String key, conditionType conditionType, sqlEngine child) {
        this.isGroup = false;
        engine.put("child", "child");
        return this.queryType(queryType).queryTable(table).queryKey(key).queryConditionType(conditionType).queryValue("(" + child.engine.get("select") + ")").queryFin(true);
    }

    /**
     * 指定主表
     *
     * @param tableName 主表的表名
     * @return
     */
    public sqlEngine execute(String tableName, String alias) {
        alias = alias == null || alias.trim().equals("") ? tableName : alias;
        engine.put("execute", tableName + " AS " + alias);
        return this;
    }

    /**
     * 指定子查询作为主表
     *
     * @param table
     * @param alias
     * @return
     */
    public sqlEngine executeChild(sqlEngine table, String alias) {
        engine.put("execute", "(" + table.engine.get("select") + ") AS " + alias);
        return this;
    }

    /**
     * 主子表筛选条件（非必要,请根据实际业务进行使用）
     *
     * @param queryType
     * @param key
     * @param conditionType
     * @param value
     * @return
     */
    public sqlEngine joinWhere(queryType queryType, String key, conditionType conditionType, String value) {
        return this.queryType(queryType).queryKey(key).queryConditionType(conditionType).queryValue(value).queryFin(false);
    }


    /**
     * 主子表关系构造器
     *
     * @param joinTable
     * @param joinType
     * @return
     */
    public sqlEngine joinBuild(String joinTable, String joinAlias, joinType joinType) {
        return this.join(joinTable, joinAlias).joinType(joinType);
    }

    /**
     * 主子表关系构造器 （复杂连表处理）
     *
     * @param joinTable
     * @param joinAlias
     * @param joinType
     * @return
     */
    public sqlEngine joinChildBuild(sqlEngine joinTable, String joinAlias, joinType joinType) {
        return this.join("(" + joinTable.engine.get("select") + ")", joinAlias).joinType(joinType);
    }

    /**
     * 主子表关系字段构造器
     *
     * @param joinTable 所连接的主表表名
     * @param joinFrom  主表外键字段
     * @param joinTo    字表连接字段
     * @return
     */
    public sqlEngine joinColunm(String joinTable, String joinFrom, String joinTo) {
        engine.put("joinColumn", joinTable + "." + joinFrom + " = " + engine.get("joinTableAlias") + "." + joinTo);
        return this;
    }

    /**
     * 完成一次主子表关联关系认证并生成对应语句
     *
     * @return
     */
    public sqlEngine joinFin() {
        StringBuffer jo = new StringBuffer();
        jo.append(engine.get("join") == null ? "" : engine.get("join"))
                .append(engine.get("joinType") == null ? joinType.L.getJoinType() : engine.get("joinType"))
                .append(engine.get("joinTable"))
                .append(" ")
                .append(engine.get("joinTableAlias"))
                .append(" ");
        //TODO: 对主子表关联条件进行匹配
        if (engine.get("joinQuery") == null) {
            jo.append("ON")
                    .append(" ")
                    .append(engine.get("joinColumn"))
                    .append(" ");
        } else {
            jo.append(" WHERE ").append(engine.get("joinQuery"));
        }
        engine.put("join", jo.toString());
        //TODO: 重置主子表筛选条件属性
        engine.remove("joinQuery");
        engine.remove("joinType");
        return this;
    }


    /**
     * 数据分页构造器
     *
     * @param pageNo   当前第几页
     * @param pageSize 每页查询/展示X条
     * @return
     */
    public sqlEngine dataPaging(Integer pageNo, Integer pageSize) {
        StringBuffer sb = new StringBuffer();
        //TODO:  pageSize为空则默认为一页15条
        pageSize = pageSize == null ? 15 : pageSize;
        //TODO:  pageNo为空则默认为第一页
        pageNo = pageNo == null || pageNo == 0 ? 1 : pageNo;
        sb.append(" LIMIT ");
        sb.append((pageNo - 1) * pageSize);
        sb.append(",");
        sb.append(pageSize);
        engine.put("dataPaging", sb.toString());
        return this;
    }

    /**
     * 数据排序构造器
     *
     * @param key
     * @param sortType
     * @return
     */
    public sqlEngine dataSort(String table,String key, sortType sortType) {
        StringBuffer sort = new StringBuffer(" ORDER BY ");
        String sortTypeStr = sortType.getSortType();

        key = table + "." + key;
        switch (sortTypeStr) {
            case "ASC":
                sort.append(" " + key + " ASC,");
                break;
            case "UASC":
                sort.append("  CONVERT( " + key + " USING GBK)  ASC,");
                break;
            case "UDESC":
                sort.append("  CONVERT( " + key + " USING GBK)  DESC,");
                break;
            default:
                sort.append(" " + key + " DESC,");
                break;
        }

        engine.put("dataSort", engine.get("dataSort") == null ? sort : (engine.getString("dataSort") + sort));
        return this;
    }

    /**
     * 声明当前查询语句已经结束并执行生产
     *
     * @return
     */
    public sqlEngine selectFin(String data) {
        StringBuffer sel = new StringBuffer();
        String dataSort = engine.get("dataSort") == null ? " " : engine.get("dataSort").toString();

        boolean caseFin = engine.get("caseFin") == null;

        sel.append(" SELECT ").append(caseFin ? " " : engine.get("caseFin"));

        //TODO: 首先判断查询字段是否存在，如不存在则判断case语句是否存在，若存在则不做任何操作反则查询全部，若字段存在，则在case语句句柄末尾添加逗号防止sql生成出错
        if (this.isGroup) {
            sel.append(engine.get("groupColumn") == null ? caseFin ? " * " : "  " : (caseFin ? " " : " , ") + engine.get("groupColumn"));
        } else {
            sel.append(engine.get("appointColumn") == null ? caseFin ? " * " : "  " : (caseFin ? " " : " , ") + engine.get("appointColumn"));
        }

        sel.append(" FROM ")
                .append(engine.get("execute"))
                .append(" ")
                .append(engine.get("join") == null ? "" : engine.get("join"))
                .append(engine.get("query") == null ? "" : (" WHERE " + engine.get("query")))
                .append(engine.get("groupBy") == null ? "" : (" GROUP BY " + engine.get("groupBy")))
                .append(engine.get("groupQuery") == null ? "" : (" HAVING " + engine.get("groupQuery")))
                .append(dataSort.substring(0, dataSort.length() - 1))
                .append(engine.get("dataPaging") == null ? "" : engine.get("dataPaging"));

        String parseSql = sel.toString().toLowerCase();

        //TODO: 数据参数赋值操作
        LinkedHashMap<String, Object> params = JSONObject.parseObject(data, new TypeReference<LinkedHashMap<String, Object>>() {});
        for (String key : params.keySet()) {
            parseSql = parseSql.replaceAll("params_"+key,params.get(key)+"");
        }

        engine.put("select", parseSql);
        //清空当前检索引擎
        this.isGroup = false;
        engine.remove("execute");
        engine.remove("child");
        engine.remove("join");
        engine.remove("query");
        engine.remove("dataSort");
        engine.remove("dataPaging");
        return this;
    }

    /**
     * 获取sql引擎处理结果
     *
     * @return
     */
    public BaseData parseSql() {
        return engine;
    }

    //TODO : 查询相关    ↓↓↓↓↓↓↓↓↓

}
