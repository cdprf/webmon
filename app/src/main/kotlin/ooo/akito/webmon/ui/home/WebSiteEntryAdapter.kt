package ooo.akito.webmon.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import ooo.akito.webmon.R
import ooo.akito.webmon.data.db.WebSiteEntry
import ooo.akito.webmon.databinding.ItemWebsiteRowBinding
import ooo.akito.webmon.utils.ExceptionCompanion.msgGlideLoadIconFailure
import ooo.akito.webmon.utils.Log
import ooo.akito.webmon.utils.Utils
import ooo.akito.webmon.utils.Utils.buildDefaultIconUrlFull
import ooo.akito.webmon.utils.Utils.currentDateTime
import ooo.akito.webmon.utils.Utils.isStatusAcceptable
import ooo.akito.webmon.utils.Utils.removeUrlProto
import ooo.akito.webmon.utils.Utils.sortedByItemPosition
import ooo.akito.webmon.utils.iconUrlFetcher
import java.util.*


class WebSiteEntryAdapter(todoEvents: WebSiteEntryEvents) : RecyclerView.Adapter<WebSiteEntryAdapter.ViewHolder>(), Filterable {

  private var mList: List<WebSiteEntry> = arrayListOf()
  private var filteredList: List<WebSiteEntry> = arrayListOf()
  private val listener: WebSiteEntryEvents = todoEvents
  private lateinit var itemWebsiteRowBinding: ItemWebsiteRowBinding

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    itemWebsiteRowBinding = ItemWebsiteRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return ViewHolder(itemWebsiteRowBinding)
  }

  override fun getItemCount(): Int = filteredList.size

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    with (filteredList[position]) {
      holder.itemView.tag = this
      holder.bind(this, listener, position)
    }
  }

  inner class ViewHolder(private val binding: ItemWebsiteRowBinding) : RecyclerView.ViewHolder(binding.root) {
    @SuppressLint("SetTextI18n")
    fun bind(webSiteEntry: WebSiteEntry, listener: WebSiteEntryEvents, position: Int) {

      binding.root.apply {

        binding.txtWebSite.text = webSiteEntry.name
        binding.txtUrl.text = webSiteEntry.url

        // TODO: 2021/12/14 Merge both Glide usages into easy to use utility method.
        if (webSiteEntry.isOnionAddress || webSiteEntry.isTcpAddress || webSiteEntry.isSmtpAddress || webSiteEntry.isImapAddress) {
          val icon = when {
            webSiteEntry.isOnionAddress -> ResourcesCompat.getDrawable(resources, R.drawable.ic_tor_onion, context.theme)
            else -> ResourcesCompat.getDrawable(resources, R.mipmap.placeholder_favicon, context.theme)
          }
          try {
            /** https://github.com/FortAwesome/Font-Awesome/issues/5101#issuecomment-298361743 */
            Glide
              .with(binding.imgLogo.context)
              .load(icon)
              .dontAnimate()
              .let { request ->
                if (binding.imgLogo.drawable != null) {
                  request.placeholder(binding.imgLogo.drawable.constantState?.newDrawable()?.mutate())
                } else {
                  request
                }
              }
              .diskCacheStrategy(DiskCacheStrategy.NONE)
              .skipMemoryCache(true)
              .apply(RequestOptions.circleCropTransform())
              .into(binding.imgLogo)
          } catch (e: Exception) {
            Log.warn(e.message ?: msgGlideLoadIconFailure)
          }
        } else {
          /**
            https://gitlab.com/fdroid/fdroiddata/-/merge_requests/10001#note_720175502
            https://github.com/mat/besticon
            https://www.zemarch.com/cropped-favicon-png/
            https://github.com/FortAwesome/Font-Awesome/issues/5101#issuecomment-298361743
          */
          val iconUrlFull = buildDefaultIconUrlFull(
            iconUrlFetcher,
            webSiteEntry.url.removeUrlProto()
          )
          try {
            /**
              https://stackoverflow.com/a/48152076/7061105
              https://futurestud.io/tutorials/glide-caching-basics
            */
            Glide
              .with(binding.imgLogo.context)
              .load(iconUrlFull)
              .dontAnimate()
              .let { request ->
                if (binding.imgLogo.drawable != null) {
                  request.placeholder(binding.imgLogo.drawable.constantState?.newDrawable()?.mutate())
                } else {
                  request
                }
              }
              .error(R.mipmap.placeholder_favicon)
              .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
              .skipMemoryCache(true)
              .apply(RequestOptions.circleCropTransform())
              .into(binding.imgLogo)
          } catch (e: Exception) {
            Log.warn(e.message ?: msgGlideLoadIconFailure)
          }
        }


        binding.txtStatus.text = HtmlCompat.fromHtml("<b>Status :</b> ${webSiteEntry.status ?: "000"} - ${Utils.getStatusMessage(webSiteEntry) /* TODO: Get actual status message. */}<br><b>Last Update :</b> ${webSiteEntry.updatedAt ?: currentDateTime()}", HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.imgIndicator.setImageResource(if(webSiteEntry.isStatusAcceptable().not()) R.drawable.ic_alert else R.drawable.ic_success)
        binding.btnPause.setImageResource(if(webSiteEntry.isPaused) R.drawable.ic_play else R.drawable.ic_pause)


        val popupMenu = PopupMenu(context, binding.btnMore)
        popupMenu.inflate(R.menu.menu_website_more)
        popupMenu.setOnMenuItemClickListener {
          when (it.itemId) {
            R.id.action_refresh -> listener.onRefreshClicked(webSiteEntry)
            R.id.action_visit -> listener.onVisitClicked(webSiteEntry)
            R.id.action_edit -> listener.onEditClicked(webSiteEntry)
            R.id.action_delete -> listener.onDeleteClicked(webSiteEntry)
          }
          true
        }

        binding.btnMore.setOnClickListener {
          try {
            val popup = PopupMenu::class.java.getDeclaredField("mPopup") //TODO: Do not use private API.
            popup.isAccessible =  true
            val menu = popup.get(popupMenu)
            menu.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java).invoke(menu, true)
          }catch (e: Exception) {
            e.printStackTrace()
          } finally {
            popupMenu.show()
          }
        }

        binding.btnPause.setOnClickListener {
          listener.onPauseClicked(webSiteEntry, position)
          webSiteEntry.isPaused = webSiteEntry.isPaused.not()
          notifyItemChanged(position)
        }


        this.setOnClickListener { listener.onRefreshClicked(webSiteEntry) }
      }
    }
  }

  /**
   * Search Filter implementation
   * */
  override fun getFilter(): Filter {
    return object : Filter() {
      override fun performFiltering(searchCharSequence: CharSequence?): FilterResults {
        val searchString = searchCharSequence.toString()
        filteredList = if (searchString.isEmpty()) {
          mList
        } else {
          val filteredList = arrayListOf<WebSiteEntry>()
          mList.forEach { webSiteEntry ->
            when {
              webSiteEntry.name
                .lowercase(Locale.getDefault())
                .contains(searchString.lowercase(Locale.getDefault())) ||
              webSiteEntry.url
                .contains(searchString
                .lowercase(Locale.getDefault())) ||
              webSiteEntry.customTags.any { it.startsWith(searchString) }
              -> {
                filteredList.add(webSiteEntry)
              }
            }
          }
          filteredList
        }

        val filterResults = FilterResults()
        filterResults.values = filteredList
        return filterResults
      }

      @SuppressLint("NotifyDataSetChanged")
      override fun publishResults(p0: CharSequence?, p1: FilterResults?) {
        filteredList = (p1?.values as List<*>)
          .filterIsInstance<WebSiteEntry>()
          .sortedByItemPosition()
        notifyDataSetChanged()
      }
    }
  }

  /**
   * Activity uses this method to update todoList with the help of LiveData
   * */
  @SuppressLint("NotifyDataSetChanged")
  fun setAllTodoItems(todoItems: List<WebSiteEntry>?, publish: Boolean = true) {
    todoItems?.let {
      mList = it
      filteredList = mList.sortedByItemPosition()
    }
    if (todoItems == null) {
      filteredList = filteredList.sortedByItemPosition()
    }
    if (publish) {
      notifyDataSetChanged()
    }
  }

  /**
   * RecycleView touch event callbacks
   * */
  interface WebSiteEntryEvents {
    fun onDeleteClicked(webSiteEntry: WebSiteEntry)
    fun onViewClicked(webSiteEntry: WebSiteEntry, adapterPosition: Int)
    fun onVisitClicked(webSiteEntry: WebSiteEntry)
    fun onEditClicked(webSiteEntry: WebSiteEntry)
    fun onRefreshClicked(webSiteEntry: WebSiteEntry)
    fun onPauseClicked(webSiteEntry: WebSiteEntry, adapterPosition: Int)
  }
}
