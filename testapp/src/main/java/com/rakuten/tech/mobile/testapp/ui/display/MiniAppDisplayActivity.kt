package com.rakuten.tech.mobile.testapp.ui.display

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.rakuten.tech.mobile.miniapp.MiniAppInfo
import com.rakuten.tech.mobile.miniapp.ads.AdMobDisplayer
import com.rakuten.tech.mobile.miniapp.navigator.MiniAppNavigator
import com.rakuten.tech.mobile.miniapp.js.MiniAppMessageBridge
import com.rakuten.tech.mobile.miniapp.js.userinfo.TokenData
import com.rakuten.tech.mobile.miniapp.js.userinfo.UserInfoBridgeDispatcher
import com.rakuten.tech.mobile.miniapp.permission.MiniAppPermissionType
import com.rakuten.tech.mobile.miniapp.navigator.ExternalResultHandler
import com.rakuten.tech.mobile.miniapp.testapp.R
import com.rakuten.tech.mobile.miniapp.testapp.databinding.MiniAppDisplayActivityBinding
import com.rakuten.tech.mobile.testapp.helper.AppPermission
import com.rakuten.tech.mobile.testapp.ui.base.BaseActivity
import com.rakuten.tech.mobile.testapp.ui.settings.AppSettings

class MiniAppDisplayActivity : BaseActivity() {

    private lateinit var appId: String
    private lateinit var miniAppMessageBridge: MiniAppMessageBridge
    private lateinit var miniAppNavigator: MiniAppNavigator
    private var miniappPermissionCallback: (isGranted: Boolean) -> Unit = {}
    private lateinit var sampleWebViewExternalResultHandler: ExternalResultHandler
    private lateinit var binding: MiniAppDisplayActivityBinding

    private val externalWebViewReqCode = 100

    companion object {
        private val appIdTag = "app_id_tag"
        private val miniAppTag = "mini_app_tag"

        fun start(context: Context, appId: String) {
            context.startActivity(Intent(context, MiniAppDisplayActivity::class.java).apply {
                putExtra(appIdTag, appId)
            })
        }

        fun start(context: Context, miniAppInfo: MiniAppInfo) {
            context.startActivity(Intent(context, MiniAppDisplayActivity::class.java).apply {
                putExtra(miniAppTag, miniAppInfo)
            })
        }
    }

    private lateinit var viewModel: MiniAppDisplayViewModel

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showBackIcon()

        if (intent.hasExtra(miniAppTag) || intent.hasExtra(appIdTag)) {
            appId = intent.getStringExtra(appIdTag) ?: ""
            if (appId.isEmpty())
                appId = intent.getParcelableExtra<MiniAppInfo>(miniAppTag)!!.id

            binding = DataBindingUtil.setContentView(this, R.layout.mini_app_display_activity)

            viewModel = ViewModelProvider.NewInstanceFactory()
                .create(MiniAppDisplayViewModel::class.java).apply {

                    setHostLifeCycle(lifecycle)
                    miniAppView.observe(this@MiniAppDisplayActivity, Observer {
                        if (ApplicationInfo.FLAG_DEBUGGABLE == 2)
                            WebView.setWebContentsDebuggingEnabled(true)
                        //action: display webview
                        setContentView(it)
                    })

                    errorData.observe(this@MiniAppDisplayActivity, Observer {
                        Toast.makeText(this@MiniAppDisplayActivity, it, Toast.LENGTH_LONG).show()
                    })

                    isLoading.observe(this@MiniAppDisplayActivity, Observer {
                        toggleProgressLoading(it)
                    })
                }

            setupMiniAppMessageBridge()

            miniAppNavigator = object : MiniAppNavigator {

                override fun openExternalUrl(url: String, externalResultHandler: ExternalResultHandler) {
                    sampleWebViewExternalResultHandler = externalResultHandler
                    WebViewActivity.startForResult(this@MiniAppDisplayActivity, url,
                        appId, externalWebViewReqCode)
                }
            }

            viewModel.obtainMiniAppDisplay(
                this@MiniAppDisplayActivity,
                appId,
                miniAppMessageBridge,
                miniAppNavigator
            )
        }
    }

    private fun setupMiniAppMessageBridge() {
        miniAppMessageBridge = object : MiniAppMessageBridge() {
            override fun getUniqueId() = AppSettings.instance.uniqueId

            override fun requestPermission(
                miniAppPermissionType: MiniAppPermissionType,
                callback: (isGranted: Boolean) -> Unit
            ) {
                miniappPermissionCallback = callback
                ActivityCompat.requestPermissions(
                    this@MiniAppDisplayActivity,
                    AppPermission.getPermissionRequest(miniAppPermissionType),
                    AppPermission.getRequestCode(miniAppPermissionType)
                )
            }
        }

        miniAppMessageBridge.setAdMobDisplayer(AdMobDisplayer(this@MiniAppDisplayActivity))
        miniAppMessageBridge.allowScreenOrientation(true)

        val userInfoBridgeDispatcher = object : UserInfoBridgeDispatcher() {
            override fun getUserName(): String = AppSettings.instance.profileName

            override fun getProfilePhoto(): String = AppSettings.instance.profilePictureUrlBase64

            override fun getAccessToken(
                miniAppId: String,
                onSuccess: (tokenData: TokenData) -> Unit,
                onError: (message: String) -> Unit
            ) {
                AlertDialog.Builder(this@MiniAppDisplayActivity)
                    .setMessage("Allow $miniAppId to get access token?")
                    .setPositiveButton(android.R.string.yes) { dialog, _ ->
                        onSuccess(AppSettings.instance.tokenData)
                        dialog.dismiss()
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        onError("$miniAppId not allowed to get access token")
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }
        }
        miniAppMessageBridge.setUserInfoBridgeDispatcher(userInfoBridgeDispatcher)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        val isGranted = !grantResults.contains(PackageManager.PERMISSION_DENIED)
        miniappPermissionCallback.invoke(isGranted)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == externalWebViewReqCode && resultCode == Activity.RESULT_OK) {
            data?.let { intent -> sampleWebViewExternalResultHandler.emitResult(intent) }
        }
    }

    private fun toggleProgressLoading(isOn: Boolean) {
        when (isOn) {
            true -> binding.pb.visibility = View.VISIBLE
            false -> binding.pb.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        if (!viewModel.canGoBackwards()) {
            super.onBackPressed()
        }
    }
}