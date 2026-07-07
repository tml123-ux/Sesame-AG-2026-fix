package fansirsqi.xposed.sesame.data;

/**
 * 用于统一管理所有【每日 / 状态 Flag】的常量定义。
 *
 * 设计目标：
 * 1. 避免项目中散落字符串常量
 * 2. 统一命名规范，便于搜索和维护
 * 3. 明确业务模块归属
 *
 * 命名规范：
 * - 常量名：全大写 + 下划线（FLAG_XXX）
 * - 常量值：实际存储使用的 Key（保持历史兼容）
 */
public final class StatusFlags {

    private StatusFlags() {
        // 禁止实例化
    }

    // ============================================================
    // Neverland（健康岛）
    // ============================================================

    /** 今日步数任务是否已完成 */
    public static final String FLAG_NEVERLAND_STEP_COUNT =
            "Flag_Neverland_StepCount";


    // ============================================================
    // AntMember（会员频道 / 积分）
    // ============================================================

    /** 是否已执行「领取所有可做芝麻任务」 */
    public static final String FLAG_ANTMEMBER_DO_ALL_SESAME_TASK =
            "AntMember::doAllAvailableSesameTask";

    /** 今日贴纸领取任务 */
    public static final String FLAG_ANTMEMBER_STICKER =
            "Flag_AntMember_Sticker";


    // ============================================================
    // 芝麻信用 / 芝麻粒
    // ============================================================

    /** 芝麻粒炼金：次日奖励是否已领取 */
    public static final String FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD =
            "zmxy::alchemy::nextDayAward";

    /** 领取所有可做芝麻任务 */
    public static final String FLAG_SESAME_DO_ALL_AVAILABLE_TASK =
            "AntSesameCredit::doAllAvailableSesameTask";

    /** 芝麻树任务今日是否已处理 */
    public static final String FLAG_SESAME_ZHIMA_TREE_TASK_HANDLED_TODAY =
            "AntSesameCredit::zhimaTreeTaskHandledToday";

    /** 芝麻加入次数是否已达上限 */
    public static final String FLAG_SESAME_JOIN_LIMIT_REACHED =
            "AntSesameCredit::sesameJoinLimitReached";

    /** 芝麻粒签到是否已完成 */
    public static final String FLAG_SESAME_ZML_CHECKIN_DONE =
            "AntSesameCredit::zmlCheckInDone";

    /** 收集芝麻是否已完成 */
    public static final String FLAG_SESAME_COLLECT_DONE =
            "AntSesameCredit::collectSesameDone";

    /** 芝麻粒炼金次日奖励 */
    public static final String FLAG_SESAME_ALCHEMY_NEXT_DAY_AWARD =
            "AntSesameCredit::alchemy::nextDayAward";

    /** 芝麻粒兑换是否已完成 */
    public static final String FLAG_SESAME_GRAIN_EXCHANGE_DONE =
            "AntSesameCredit::sesameGrainExchangeDone";

    /** 信用2101事件计数前缀 */
    public static final String FLAG_CREDIT2101_EVENT_COUNT_PREFIX =
            "2101_Event_";

    /** 信用2101事件计数后缀 */
    public static final String FLAG_CREDIT2101_EVENT_COUNT_SUFFIX =
            "_COUNT_TODAY";

    /** 信用 2101：图鉴章节任务是否全部完成 */
    public static final String FLAG_CREDIT2101_CHAPTER_TASK_DONE =
            "FLAG_Credit2101_ChapterTask_Done";


    // ============================================================
    // 福气鱼池（FishPond）
    // ============================================================

    /** 福气鱼池：签到是否已完成 */
    public static final String FLAG_ANTFISHPOND_SIGN_DONE =
            "AntFishPond::signDone";

    /** 福气鱼池：礼盒是否已领取 */
    public static final String FLAG_ANTFISHPOND_GIFT_BOX_DONE =
            "AntFishPond::giftBoxDone";

    /** 福气鱼池：明日钓竿是否已领取 */
    public static final String FLAG_ANTFISHPOND_TOMORROW_ROD_DONE =
            "AntFishPond::tomorrowRodDone";

    /** 福气鱼池：任务是否已完成 */
    public static final String FLAG_ANTFISHPOND_TASKS_DONE =
            "AntFishPond::tasksDone";

    /** 福气鱼池：风险令牌是否缺失 */
    public static final String FLAG_ANTFISHPOND_RISK_TOKEN_MISSING =
            "AntFishPond::riskTokenMissing";

    /** 福气鱼池：鱼数量 */
    public static final String FLAG_ANTFISHPOND_FISH_COUNT =
            "AntFishPond::fishCount";

    /** 福气鱼池：鱼数量是否已达上限 */
    public static final String FLAG_ANTFISHPOND_FISH_LIMIT_REACHED =
            "AntFishPond::fishLimitReached";


    // ============================================================
    // 网商银行（MyBank）
    // ============================================================

    /** 网商银行：福利签到是否已完成 */
    public static final String FLAG_MYBANK_WELFARE_SIGN_DONE =
            "MyBankWelfare::signDone";

    /** 网商银行：兑换刷新是否已完成 */
    public static final String FLAG_MYBANK_WELFARE_EXCHANGE_REFRESH_DONE =
            "MyBankWelfare::exchangeRefreshDone";


    // ============================================================
    // 运动任务（AntSports）
    // ============================================================

    /** 运动任务大厅：今日是否已循环处理 */
    public static final String FLAG_ANTSPORTS_TASK_CENTER_DONE =
            "Flag_AntSports_TaskCenter_Done";

    /** 今日步数同步是否已完成 */
    public static final String FLAG_ANTSPORTS_SYNC_STEP_DONE =
            "FLAG_ANTSPORTS_syncStep_Done";

    /** 今日运动日常任务是否已完成 */
    public static final String FLAG_ANTSPORTS_DAILY_TASKS_DONE =
            "FLAG_ANTSPORTS_dailyTasks_Done";


    // ============================================================
    // 农场 / 新村 / 团队
    // ============================================================

    /** 团队浇水：今日次数统计 */
    public static final String FLAG_TEAM_WATER_DAILY_COUNT =
            "Flag_Team_Weater_Daily_Count";

    /** 农场组件：每日回访奖励 */
    public static final String FLAG_ANTORCHARD_WIDGET_DAILY_AWARD =
            "Flag_Antorchard_Widget_Daily_Award";

    /** 农场：今日施肥次数 */
    public static final String FLAG_ANTORCHARD_SPREAD_MANURE_COUNT =
            "FLAG_Antorchard_SpreadManure_Count";

    /** 蚂蚁新村：今日丢肥料是否达到上限 */
    public static final String FLAG_ANTSTALL_THROW_MANURE_LIMIT =
            "Flag_AntStall_Throw_Manure_Limit";

}
