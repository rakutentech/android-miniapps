package com.rakuten.tech.mobile.miniapp.js

import androidx.annotation.Keep

@Keep
internal data class CallbackObj(
    var action: String,
    var param: Any?,
    var id: String
)
