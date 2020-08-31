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
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.rakuten.tech.mobile.miniapp.MiniAppInfo
import com.rakuten.tech.mobile.miniapp.navigator.MiniAppNavigator
import com.rakuten.tech.mobile.miniapp.js.MiniAppMessageBridge
import com.rakuten.tech.mobile.miniapp.js.MiniAppPermissionType
import com.rakuten.tech.mobile.miniapp.navigator.ExternalUrlHandler
import com.rakuten.tech.mobile.miniapp.navigator.MiniAppExternalUrlLoader
import com.rakuten.tech.mobile.miniapp.testapp.R
import com.rakuten.tech.mobile.testapp.helper.AppPermission
import com.rakuten.tech.mobile.testapp.ui.base.BaseActivity
import com.rakuten.tech.mobile.testapp.ui.settings.AppSettings
import kotlinx.android.synthetic.main.mini_app_display_activity.*

class MiniAppDisplayActivity : BaseActivity() {

    private lateinit var appId: String
    private lateinit var miniAppMessageBridge: MiniAppMessageBridge
    private lateinit var miniAppNavigator: MiniAppNavigator
    private var miniappPermissionCallback: (isGranted: Boolean) -> Unit = {}
    private lateinit var sampleWebViewExternalUrlHandler: ExternalUrlHandler

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

            setContentView(R.layout.mini_app_display_activity)

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

            miniAppNavigator = object : MiniAppNavigator {

                override fun openExternalUrl(url: String, externalUrlHandler: ExternalUrlHandler) {
                    sampleWebViewExternalUrlHandler = externalUrlHandler

                    val intent = Intent(this@MiniAppDisplayActivity, WebViewActivity::class.java).apply {
                        putExtra(WebViewActivity.loadUrlTag, url)
                        putExtra(WebViewActivity.miniAppIdTag, intent.getParcelableExtra<MiniAppInfo>(miniAppTag)!!.id)
                    }
                    startActivityForResult(intent, externalWebViewReqCode)
                }
            }

            if (appId.isEmpty())
                viewModel.obtainMiniAppDisplay(
                    this@MiniAppDisplayActivity,
                    intent.getParcelableExtra<MiniAppInfo>(miniAppTag)!!.id,
                    miniAppMessageBridge,
                    miniAppNavigator
                )
            else
                viewModel.obtainMiniAppDisplay(
                    this@MiniAppDisplayActivity,
                    appId,
                    miniAppMessageBridge,
                    miniAppNavigator
                )
        }
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
            data?.let { intent ->
                if (intent.hasExtra(MiniAppExternalUrlLoader.returnUrlTag))
                    sampleWebViewExternalUrlHandler.emitResult(
                        intent.getStringExtra(MiniAppExternalUrlLoader.returnUrlTag)!!)
            }
        }
    }

    private fun toggleProgressLoading(isOn: Boolean) {
        if (findViewById<View>(R.id.pb) != null) {
            when (isOn) {
                true -> pb.visibility = View.VISIBLE
                false -> pb.visibility = View.GONE
            }
        }
    }

    override fun onBackPressed() {
        if (!viewModel.canGoBackwards()) {
            super.onBackPressed()
        }
    }
}
