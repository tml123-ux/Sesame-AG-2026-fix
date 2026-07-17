package fansirsqi.xposed.sesame.task.workPoint;

import fansirsqi.xposed.sesame.hook.RequestManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 工分模块 RPC - 基于 IMASP programInvoke 协议
 *
 * 工分系统位于支付宝就业(job-center)右侧入口,使用 alipay.imasp.program.programInvoke 统一入口。
 * 不经过 IEP 任务框架,所有操作通过特定组件 ID 完成。
 *
 * 抓包来源: 支付宝 12.12.6.8000, job-right-center 场景
 */
public class WorkPointRpcCall {

    private static final String PROGRAM_ID = "independent_component_program2024121902034600";
    private static final String SOURCE = "job-right-center";

    // 签到组件
    private static final String SIGN_IN_COMPONENT = "independent_component_sign_in_01961456";
    private static final String COMP_SIGN_IN_RECALL = SIGN_IN_COMPONENT + "_independent_component_sign_in_recall";
    private static final String COMP_SIGN_IN_SIGN = SIGN_IN_COMPONENT + "_independent_component_sign_in_sign";
    private static final String COMP_SIGN_IN_QUERY = SIGN_IN_COMPONENT + "_independent_component_sign_in_query_history";

    // 任务组件
    private static final String TASK_COMPONENT = "independent_component_task_reward_01961455";
    private static final String COMP_TASK_QUERY = TASK_COMPONENT + "_independent_component_task_reward_query";
    private static final String COMP_TASK_APPLY = TASK_COMPONENT + "_independent_component_task_reward_apply";
    private static final String COMP_TASK_PROCESS = TASK_COMPONENT + "_independent_component_task_reward_process";

    /**
     * 签到-查询当前签到状态 (recall)
     * 返回今天的签到状态、连续签到天数、每日奖励配置等
     */
    public static String signInRecall() throws JSONException {
        return buildProgramInvoke(COMP_SIGN_IN_RECALL, null);
    }

    /**
     * 签到-执行签到 (sign)
     * @param signInCode 签到计划码,如 "SIG2025010702647012"
     */
    public static String signInSubmit(String signInCode) throws JSONException {
        JSONObject compParams = new JSONObject();
        compParams.put("code", signInCode);
        return buildProgramInvoke(COMP_SIGN_IN_SIGN, compParams);
    }

    /**
     * 签到-查询历史签到记录
     * @param signInCode 签到计划码,如 "SIG2025010702647012"
     * @param startDate 开始日期,如 "20260601"
     * @param endDate   结束日期,如 "20260717"
     */
    public static String signInQueryHistory(String signInCode, String startDate, String endDate) throws JSONException {
        JSONObject compParams = new JSONObject();
        compParams.put("code", signInCode);
        JSONObject dateRange = new JSONObject();
        dateRange.put("endDate", endDate);
        dateRange.put("startDate", startDate);
        compParams.put("cycleDateRange", dateRange);
        return buildProgramInvoke(COMP_SIGN_IN_QUERY, compParams);
    }

    /**
     * 查询工分任务列表
     */
    public static String queryTaskList() throws JSONException {
        return buildProgramInvoke(COMP_TASK_QUERY, null);
    }

    /**
     * 领取(报名)工分任务
     * @param taskCode 任务码,如 "TT2026071502419199"
     */
    public static String applyTask(String taskCode) throws JSONException {
        JSONObject compParams = new JSONObject();
        compParams.put("code", taskCode);
        return buildProgramInvoke(COMP_TASK_APPLY, compParams);
    }

    /**
     * 完成工分任务(提交任务结果)
     * @param taskCode 任务码
     * @param outBizNo 领取时返回的业务流水号(时间戳)
     * @param recordNo 领取时返回的记录号,如 "GTP2026071800085009460611466"
     */
    public static String processTask(String taskCode, long outBizNo, String recordNo) throws JSONException {
        JSONObject compParams = new JSONObject();
        compParams.put("code", taskCode);
        compParams.put("outBizNo", outBizNo);
        compParams.put("recordNo", recordNo);
        return buildProgramInvoke(COMP_TASK_PROCESS, compParams);
    }

    /**
     * 构建 programInvoke 请求
     * @param componentIdentify 组件标识(全路径)
     * @param compParams        组件参数(可为null表示{}空参数)
     */
    private static String buildProgramInvoke(String componentIdentify, JSONObject compParams) throws JSONException {
        JSONObject components = new JSONObject();
        if (compParams == null) {
            compParams = new JSONObject();
        }
        components.put(componentIdentify, compParams);

        JSONObject body = new JSONObject();
        body.put("components", components);
        body.put("deviceInfo", new JSONObject());
        body.put("operationParamIdentify", PROGRAM_ID);
        body.put("source", SOURCE);
        // channel only on query
        if (componentIdentify.equals(COMP_TASK_QUERY)) {
            body.put("channel", SOURCE);
        }

        return RequestManager.requestString(
            "alipay.imasp.program.programInvoke",
            new JSONArray().put(body).toString()
        );
    }
}
