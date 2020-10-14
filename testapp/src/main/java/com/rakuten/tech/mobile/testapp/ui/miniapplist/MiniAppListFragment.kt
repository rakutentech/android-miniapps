package com.rakuten.tech.mobile.testapp.ui.miniapplist

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.rakuten.tech.mobile.miniapp.MiniAppInfo
import com.rakuten.tech.mobile.miniapp.testapp.R
import com.rakuten.tech.mobile.miniapp.testapp.databinding.MiniAppListFragmentBinding
import com.rakuten.tech.mobile.testapp.adapter.MiniAppList
import com.rakuten.tech.mobile.testapp.adapter.MiniAppListAdapter
import com.rakuten.tech.mobile.testapp.helper.MiniAppListStore
import com.rakuten.tech.mobile.testapp.launchActivity
import com.rakuten.tech.mobile.testapp.ui.base.BaseFragment
import com.rakuten.tech.mobile.testapp.ui.display.MiniAppDisplayActivity
import com.rakuten.tech.mobile.testapp.ui.input.MiniAppInputActivity
import com.rakuten.tech.mobile.testapp.ui.settings.OnSearchListener
import kotlinx.android.synthetic.main.mini_app_list_fragment.*
import java.util.*
import kotlin.collections.ArrayList

class MiniAppListFragment : BaseFragment(), MiniAppList, OnSearchListener,
    SearchView.OnQueryTextListener {

    companion object {
        val TAG = MiniAppListFragment::class.java.canonicalName
        fun newInstance(): MiniAppListFragment = MiniAppListFragment()
    }

    private lateinit var viewModel: MiniAppListViewModel
    private lateinit var binding: MiniAppListFragmentBinding
    private lateinit var miniAppListAdapter: MiniAppListAdapter
    private lateinit var searchView: SearchView

    private var fetchedMiniAppList: List<MiniAppInfo> = listOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)

        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.mini_app_list_fragment,
            container,
            false
        )
        binding.fragment = this
        binding.rvMiniAppList.layoutManager = LinearLayoutManager(this.context)
        miniAppListAdapter = MiniAppListAdapter(ArrayList(), this)
        binding.rvMiniAppList.adapter = miniAppListAdapter
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        swipeRefreshLayout.post {
            swipeRefreshLayout.isRefreshing = true
            executeLoadingList()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel =
            ViewModelProvider.NewInstanceFactory().create(MiniAppListViewModel::class.java).apply {
                miniAppListData.observe(viewLifecycleOwner, Observer {
                    swipeRefreshLayout.isRefreshing = false
                    addMiniAppList(it)
                    MiniAppListStore.instance.saveMiniAppList(it)
                })
                errorData.observe(viewLifecycleOwner, Observer {
                    val list = MiniAppListStore.instance.getMiniAppList()
                    if (list.isEmpty())
                        updateEmptyView(list)
                    else {
                        addMiniAppList(list)
                        swipeRefreshLayout.isRefreshing = false
                    }
                })
            }

        swipeRefreshLayout.setOnRefreshListener {
            executeLoadingList()
            resetSearchBox()
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.invalidateOptionsMenu()
    }

    private fun addMiniAppList(list: List<MiniAppInfo>) {
        fetchedMiniAppList = list
        updateMiniAppListState(list)
    }

    private fun updateMiniAppListState(list: List<MiniAppInfo>) {
        miniAppListAdapter.addListWithSection(list)
        updateEmptyView(list)
    }

    private fun executeLoadingList() {
        viewModel.getMiniAppList()
    }

    override fun onMiniAppItemClick(miniAppInfo: MiniAppInfo) {
        raceExecutor.run {
            activity?.let {
                MiniAppDisplayActivity.start(it, miniAppInfo)
            }
        }
    }

    fun switchToInput() {
        raceExecutor.run { activity?.launchActivity<MiniAppInputActivity>() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> activity?.onSearchRequested() ?: false
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear() // clearing the previous inflation of menus from MenuBaseActivity
        inflater.inflate(R.menu.main, menu)

        val searchManager = activity?.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val itemSearch = menu.findItem(R.id.action_search)
        searchView = itemSearch.actionView as SearchView
        searchView.let {
            it.setSearchableInfo(searchManager.getSearchableInfo(activity?.componentName))
            it.setOnQueryTextListener(this)
        }
        // by default, search menu is hidden, show search menu here
        itemSearch.isVisible = true

        val closeButton = searchView.findViewById<ImageView>(R.id.search_close_btn)
        closeButton.setOnClickListener { resetSearchBox() }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        resetSearchBox()
    }

    override fun startSearch(query: String?) {
        updateMiniAppListState(produceSearchResult(query))
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        if (searchView.isIconified)
            return true

        updateMiniAppListState(produceSearchResult(newText))
        return true
    }

    private fun produceSearchResult(newText: String?): List<MiniAppInfo> {
        return fetchedMiniAppList.filter { info ->
            info.displayName.toLowerCase(Locale.ROOT)
                .contains(newText.toString().toLowerCase(Locale.ROOT))
        }
    }

    private fun resetSearchBox() {
        if (searchView.query.isNotEmpty() || !searchView.isIconified) {
            searchView.onActionViewCollapsed()
        }
    }

    private fun updateEmptyView(collection: List<MiniAppInfo>) {
        if (collection.isEmpty())
            emptyView.visibility = View.VISIBLE
        else
            emptyView.visibility = View.GONE
    }
}
