package com.moko.downkyi.re.core.network

/**
 * B 站接口返回的业务错误。
 *
 * 携带错误码而不是把 message 拍平成一个字符串，是因为上层需要**按码分流**：
 * `-412` 要提示去登录而不是重试，`-101` 要提示 Cookie 过期，
 * 而网络层的 `-2` 才该走重试链路。全都当成普通异常的话，
 * 界面上只会出现一句「请求失败」。
 */
class ApiException(
    val code: Int,
    override val message: String,
    val url: String = "",
) : Exception("[$code] $message") {

    /**
     * 是否属于「需要登录」类错误。
     *
     * 这三个码在 B 站侧的含义高度重叠，且都指向同一个用户动作 ——
     * 重新登录。因此不分别处理。
     */
    val needLogin: Boolean
        get() = code == CODE_NOT_LOGIN || code == CODE_FORBIDDEN || code == CODE_BAD_REQUEST

    /** 是否属于风控拦截。应对手段是错开请求节奏，而不是重试得更快。 */
    val isRiskControl: Boolean get() = code == CODE_RISK_CONTROL

    companion object {
        const val CODE_OK = 0

        /** 账号未登录 / Cookie 失效。 */
        const val CODE_NOT_LOGIN = -101

        /** 访问权限不足（未登录、或内容需要更高权限）。 */
        const val CODE_FORBIDDEN = -403

        /** 请求参数不合法，也常在 Cookie 缺失时出现。 */
        const val CODE_BAD_REQUEST = -400

        /** 风控拦截。批量任务同一瞬间打接口时最容易触发。 */
        const val CODE_RISK_CONTROL = -412

        /** 本地归一类的错误：数据不完整、校验不通过。 */
        const val CODE_LOCAL = -1

        /** 网络层失败（连接、超时、DNS）。 */
        const val CODE_NETWORK = -2

        /** 读取停滞（连接还在，但长时间没有数据）。 */
        const val CODE_STALLED = -3

        /** 服务端忽略了 Range 请求，返回了完整文件。 */
        const val CODE_RANGE_IGNORED = -4

        /** 接口返回的 JSON 结构不是预期的对象。 */
        const val CODE_MALFORMED = -5
    }
}
