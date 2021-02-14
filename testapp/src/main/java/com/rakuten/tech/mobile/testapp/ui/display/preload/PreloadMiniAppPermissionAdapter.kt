package com.rakuten.tech.mobile.testapp.ui.display.preload

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.rakuten.tech.mobile.miniapp.permission.MiniAppCustomPermissionResult
import com.rakuten.tech.mobile.miniapp.permission.MiniAppCustomPermissionType
import com.rakuten.tech.mobile.miniapp.testapp.databinding.ItemListManifestPermissionBinding
import com.rakuten.tech.mobile.testapp.ui.permission.toReadableName

class PreloadMiniAppPermissionAdapter :
    RecyclerView.Adapter<PreloadMiniAppPermissionAdapter.ViewHolder?>() {

    private var manifestPermissionNames = ArrayList<MiniAppCustomPermissionType>()
    private var manifestPermissionResults = ArrayList<MiniAppCustomPermissionResult>()
    var manifestPermissionPairs =
        arrayListOf<Pair<MiniAppCustomPermissionType, MiniAppCustomPermissionResult>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ItemListManifestPermissionBinding.inflate(layoutInflater, parent, false)

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.permissionName.text = toReadableName(manifestPermissionNames[position])
        holder.permissionSwitch.isChecked =
            permissionResultToChecked(manifestPermissionResults[position])

        // TODO: "required" permissions should be just listed with a label "Required"
        // TODO: "optional" permissions should have a toggle switch to enable/disable the permission
        holder.permissionStatus.text = "(Status: N/A)"

        holder.permissionSwitch.setOnCheckedChangeListener { _, _ ->
            manifestPermissionPairs.removeAt(position)
            manifestPermissionPairs.add(
                position,
                Pair(
                    manifestPermissionNames[position],
                    permissionResultToText(holder.permissionSwitch.isChecked)
                )
            )
        }
    }

    override fun getItemCount(): Int = manifestPermissionNames.size

    fun addManifestPermissionList(
        names: ArrayList<MiniAppCustomPermissionType>,
        results: ArrayList<MiniAppCustomPermissionResult>
    ) {
        manifestPermissionNames = names
        manifestPermissionResults = results
        manifestPermissionNames.forEachIndexed { position, _ ->
            manifestPermissionPairs.add(
                position,
                Pair(manifestPermissionNames[position], manifestPermissionResults[position])
            )
        }
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: ItemListManifestPermissionBinding) :
        RecyclerView.ViewHolder(itemView.root) {
        val permissionName: TextView = itemView.manifestPermissionName
        val permissionSwitch: SwitchCompat = itemView.manifestPermissionSwitch
        val permissionStatus: TextView = itemView.manifestPermissionStatus
    }

    private fun permissionResultToText(isChecked: Boolean): MiniAppCustomPermissionResult {
        if (isChecked)
            return MiniAppCustomPermissionResult.ALLOWED

        return MiniAppCustomPermissionResult.DENIED
    }

    private fun permissionResultToChecked(result: MiniAppCustomPermissionResult): Boolean {
        if (result == MiniAppCustomPermissionResult.ALLOWED)
            return true

        return false
    }
}
