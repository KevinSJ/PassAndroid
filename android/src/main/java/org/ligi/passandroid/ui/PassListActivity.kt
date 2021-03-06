package org.ligi.passandroid.ui

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.view.ViewPager
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import kotlinx.android.synthetic.main.pass_list.*
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.ligi.axt.AXT
import org.ligi.axt.listeners.ActivityFinishingOnClickListener
import org.ligi.passandroid.App
import org.ligi.passandroid.R
import org.ligi.passandroid.events.PassStoreChangeEvent
import org.ligi.passandroid.events.ScanFinishedEvent
import org.ligi.passandroid.events.ScanProgressEvent
import org.ligi.passandroid.helper.PassTemplates
import org.ligi.passandroid.model.PassStoreProjection
import org.ligi.snackengage.SnackContext
import org.ligi.snackengage.SnackEngage
import org.ligi.snackengage.conditions.AfterNumberOfOpportunities
import org.ligi.snackengage.conditions.connectivity.IsConnectedViaWiFiOrUnknown
import org.ligi.snackengage.snacks.BaseSnack
import org.ligi.snackengage.snacks.DefaultRateSnack
import org.ligi.snackengage.snacks.OpenURLSnack
import org.ligi.tracedroid.TraceDroid
import org.ligi.tracedroid.sending.TraceDroidEmailSender
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnNeverAskAgain
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions

@RuntimePermissions
class PassListActivity : PassAndroidActivity() {

    companion object {
        const val VERSION_STARTING_TO_SUPPORT_STORAGE_FRAMEWORK = 19
    }

    private val OPEN_FILE_READ_REQUEST_CODE = 1000

    private val drawerToggle by lazy { ActionBarDrawerToggle(this, drawer_layout, R.string.drawer_open, R.string.drawer_close) }

    private val adapter by lazy { PassTopicFragmentPagerAdapter(passStore.classifier, supportFragmentManager) }
    private val pd by lazy { ProgressDialog(this) }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onScanProgress(event: ScanProgressEvent) {
        if (pd.isShowing) {
            pd.setMessage(event.message)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onScanFinished(event: ScanFinishedEvent) {

        if (pd.isShowing) {
            val message = getString(R.string.scan_finished_dialog_text, event.foundPasses.size)
            pd.dismiss()
            AlertDialog.Builder(this@PassListActivity).setTitle(R.string.scan_finished_dialog_title).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
        }

    }

    @TargetApi(16)
    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    @OnNeverAskAgain(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun showDeniedFor() {
        Snackbar.make(fam, "no permission to scan", Snackbar.LENGTH_INDEFINITE).show()
    }

    @TargetApi(16)
    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun scan() {
        val intent = Intent(this, SearchPassesIntentService::class.java)
        startService(intent)

        pd.setTitle(R.string.scan_progressdialog_title)
        pd.setMessage(getString(R.string.scan_progressdialog_message))
        pd.setCancelable(false)
        pd.isIndeterminate = true
        pd.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.scan_dialog_send_background_button), ActivityFinishingOnClickListener(this))
        pd.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PassListActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults)
    }

    @TargetApi(VERSION_STARTING_TO_SUPPORT_STORAGE_FRAMEWORK)
    internal fun onAddOpenFileClick() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*" // tried with octet stream - no use
            startActivityForResult(intent, OPEN_FILE_READ_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(fam, "Unavailable", Snackbar.LENGTH_LONG).show()
        }

    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == OPEN_FILE_READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                val targetIntent = Intent(this, PassImportActivity::class.java)
                targetIntent.data = resultData.data
                startActivity(targetIntent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        App.component().inject(this)

        setContentView(R.layout.pass_list)

        setSupportActionBar(toolbar)

        AXT.at(fab_action_open_file).setVisibility(Build.VERSION.SDK_INT >= VERSION_STARTING_TO_SUPPORT_STORAGE_FRAMEWORK)

        // don't want too many windows in worst case - so check for errors first
        if (TraceDroid.getStackTraceFiles().size > 0) {
            tracker.trackEvent("ui_event", "send", "stacktraces", null)
            if (settings.doTraceDroidEmailSend()) {
                TraceDroidEmailSender.sendStackTraces("ligi@ligi.de", this)
            }
        } else { // if no error - check if there is a new version of the app
            tracker.trackEvent("ui_event", "processFile", "updatenotice", null)

            SnackEngage.from(fam).withSnack(DefaultRateSnack().withDuration(BaseSnack.DURATION_INDEFINITE)).withSnack(object : OpenURLSnack("market://details?id=org.ligi.survivalmanual", "survival") {

                override fun createSnackBar(snackContext: SnackContext): Snackbar {
                    val snackbar = super.createSnackBar(snackContext)

                    val textView = snackbar.view.findViewById(android.support.design.R.id.snackbar_text) as TextView
                    textView.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.survival, 0, 0, 0)
                    textView.compoundDrawablePadding = resources.getDimensionPixelOffset(R.dimen.rhythm)

                    return snackbar
                }

            }.overrideTitleText("Other App by ligi:\nFree Offline Survival Guide").overrideActionText("Get It!").withDuration(BaseSnack.DURATION_INDEFINITE).withConditions(AfterNumberOfOpportunities(7), IsConnectedViaWiFiOrUnknown())).build().engageWhenAppropriate()
        }


        drawer_layout.addDrawerListener(drawerToggle)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        view_pager.adapter = adapter

        if (adapter.count > 0) {
            view_pager.currentItem = state.lastSelectedTab
        }

        view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageSelected(position: Int) {
                state.lastSelectedTab = position
                supportInvalidateOptionsMenu()
            }

            override fun onPageScrollStateChanged(state: Int) {

            }
        })
        passStore.syncPassStoreWithClassifier(getString(R.string.topic_new))

        onPassStoreChangeEvent(null)

        fab_action_create_pass.setOnClickListener {
            val pass = PassTemplates.createAndAddEmptyPass(passStore, resources)

            fam.collapse()
            AXT.at(this).startCommonIntent().activityFromClass(PassEditActivity::class.java)

            val newTitle: String
            if (tab_layout.selectedTabPosition < 0) {
                newTitle = getString(R.string.topic_new)
            } else {
                newTitle = adapter.getPageTitle(tab_layout.selectedTabPosition).toString()
            }

            passStore.classifier.moveToTopic(pass, newTitle)
        }

        fab_action_scan.setOnClickListener {
            PassListActivityPermissionsDispatcher.scanWithCheck(this)
            fam.collapse()
        }

        fab_action_demo_pass.setOnClickListener {
            AXT.at(this).startCommonIntent().openUrl("http://espass.it/examples")
            fam.collapse()
        }

        fab_action_open_file.setOnClickListener {
            onAddOpenFileClick()
        }
    }


    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.menu_help -> {
            AXT.at(this).startCommonIntent().activityFromClass(HelpActivity::class.java)
            true
        }

        R.id.menu_emptytrash -> {
            AlertDialog.Builder(this).setMessage(getString(R.string.empty_trash_dialog_message)).setIcon(R.drawable.ic_alert_warning).setTitle(getString(R.string.empty_trash_dialog_title)).setPositiveButton(R.string.emtytrash_label) { dialog, which ->
                val passStoreProjection = PassStoreProjection(passStore,
                        getString(R.string.topic_trash),
                        null)

                for (pass in passStoreProjection.passList) {
                    passStore.deletePassWithId(pass.id)
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
            true
        }

        else -> drawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()

        bus.register(this)

        adapter.notifyDataSetChanged()
        onPassStoreChangeEvent(null)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_emptytrash).isVisible = adapter.count > 0 && adapter.getPageTitle(view_pager.currentItem) == getString(R.string.topic_trash)
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_pass_list_view, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onPause() {
        bus.unregister(this)
        super.onPause()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPassStoreChangeEvent(passStoreChangeEvent: PassStoreChangeEvent?) {
        adapter.notifyDataSetChanged()

        setupWithViewPagerIfNeeded()

        supportInvalidateOptionsMenu()

        val empty = passStore.classifier.topicByIdMap.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        tab_layout.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun setupWithViewPagerIfNeeded() {
        if (!areTabLayoutAndViewPagerInSync()) {
            tab_layout.setupWithViewPager(view_pager)
        }
    }

    private fun areTabLayoutAndViewPagerInSync(): Boolean {

        if (adapter.count != tab_layout.tabCount) {
            return false
        }

        for (i in 0..adapter.count - 1) {
            val tabAt = tab_layout.getTabAt(i)
            if (tabAt == null || adapter.getPageTitle(i) != tabAt.text) {
                return false
            }
        }
        return true

    }

}