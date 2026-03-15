package com.emuji.emuji

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.emuji.emuji.databinding.DialogEmojiKeyboardBinding

/**
 * Bottom sheet dialog that displays an emoji keyboard for user selection.
 * Provides a grid of commonly used emojis organized by categories.
 * Enhanced with search, categories, recent emojis, smooth animations and haptic feedback.
 */
class EmojiKeyboardDialog : BottomSheetDialogFragment() {
    private var _binding: DialogEmojiKeyboardBinding? = null
    private val binding get() = _binding!!
    
    private var emojiSelectedListener: ((String) -> Unit)? = null
    private var vibrator: Vibrator? = null
    private lateinit var adapter: EmojiAdapter
    private var allEmojis: List<String> = emptyList()
    private var recentEmojis: MutableList<String> = mutableListOf()
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEmojiKeyboardBinding.inflate(inflater, container, false)
        vibrator = getSystemService(requireContext(), Vibrator::class.java)
        prefs = requireContext().getSharedPreferences("emoji_prefs", Context.MODE_PRIVATE)
        loadRecentEmojis()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        allEmojis = getEmojis()
        setupEmojiGrid()
        setupSearchBar()
        setupCategoryFilters()
        animateEntrance()
    }
    
    /**
     * Loads recently used emojis from SharedPreferences
     */
    private fun loadRecentEmojis() {
        val recentString = prefs.getString("recent_emojis", "") ?: ""
        recentEmojis = if (recentString.isNotEmpty()) {
            recentString.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }
    
    /**
     * Saves recently used emojis to SharedPreferences
     */
    private fun saveRecentEmojis() {
        prefs.edit().putString("recent_emojis", recentEmojis.joinToString(",")).apply()
    }
    
    /**
     * Adds an emoji to recent list
     */
    private fun addToRecent(emoji: String) {
        recentEmojis.remove(emoji) // Remove if exists
        recentEmojis.add(0, emoji) // Add to front
        if (recentEmojis.size > 30) { // Keep only last 30
            recentEmojis = recentEmojis.take(30).toMutableList()
        }
        saveRecentEmojis()
    }
    
    /**
     * Animates the dialog entrance
     */
    private fun animateEntrance() {
        val slideUpAnimation = AnimationUtils.loadAnimation(context, R.anim.slide_in_right)
        binding.emojiRecyclerView.startAnimation(slideUpAnimation)
    }

    /**
     * Sets up the emoji grid with all available emojis
     */
    private fun setupEmojiGrid() {
        adapter = EmojiAdapter(allEmojis) { emoji ->
            performHapticFeedback()
            addToRecent(emoji)
            emojiSelectedListener?.invoke(emoji)
            dismiss()
        }

        binding.emojiRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 7) // 7 columns for a good mobile layout
            this.adapter = this@EmojiKeyboardDialog.adapter
            // Add item animator for smooth animations
            itemAnimator?.apply {
                addDuration = 150
                removeDuration = 150
            }
        }
    }
    
    /**
     * Sets up the search bar functionality
     */
    private fun setupSearchBar() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterEmojis(s.toString())
            }
        })
    }
    
    /**
     * Sets up category filter chips
     */
    private fun setupCategoryFilters() {
        binding.chipRecent.setOnClickListener { 
            performHapticFeedback()
            showCategory("recent") 
        }
        binding.chipSmileys.setOnClickListener { 
            performHapticFeedback()
            showCategory("smileys") 
        }
        binding.chipMusic.setOnClickListener { 
            performHapticFeedback()
            showCategory("music") 
        }
        binding.chipHearts.setOnClickListener { 
            performHapticFeedback()
            showCategory("hearts") 
        }
        binding.chipAll.setOnClickListener { 
            performHapticFeedback()
            showCategory("all") 
        }
    }
    
    /**
     * Filters emojis based on search query
     */
    private fun filterEmojis(query: String) {
        val filtered = if (query.isEmpty()) {
            allEmojis
        } else {
            // For now, just show all since we don't have keyword mapping
            // In a production app, you'd have emoji keywords/descriptions
            allEmojis
        }
        
        updateEmojiList(filtered)
    }
    
    /**
     * Shows emojis for a specific category
     */
    private fun showCategory(category: String) {
        binding.searchEditText.text?.clear() // Clear search when selecting category
        
        val filtered = when (category) {
            "recent" -> {
                if (recentEmojis.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.emojiRecyclerView.visibility = View.GONE
                    binding.emptyStateText.text = "No recent emojis yet"
                    return
                }
                recentEmojis
            }
            "smileys" -> getSmileyEmojis()
            "music" -> getMusicEmojis()
            "hearts" -> getHeartEmojis()
            else -> allEmojis
        }
        
        updateEmojiList(filtered)
    }
    
    /**
     * Updates the emoji list in the adapter
     */
    private fun updateEmojiList(emojis: List<String>) {
        if (emojis.isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.emojiRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateText.visibility = View.GONE
            binding.emojiRecyclerView.visibility = View.VISIBLE
            adapter.updateEmojis(emojis)
        }
    }
    
    /**
     * Provides haptic feedback for emoji selection
     */
    private fun performHapticFeedback() {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(15)
            }
        }
    }

    /**
     * Sets the callback for when an emoji is selected
     */
    fun setOnEmojiSelectedListener(listener: (String) -> Unit) {
        emojiSelectedListener = listener
    }

    /**
     * Returns smiley and emotion emojis
     */
    private fun getSmileyEmojis(): List<String> {
        return listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
            "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐",
            "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
            "😌", "😔", "😪", "🤤", "😴", "😎", "🤓", "🧐",
            "😕", "😟", "🙁", "☹️", "😮", "😯", "😲", "😳",
            "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭",
            "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱",
            "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️",
            "🤠", "🥳", "🥸", "🥵", "🥶", "😶‍🌫️", "😵", "😵‍💫", "🤯"
        )
    }
    
    /**
     * Returns music and entertainment emojis
     */
    private fun getMusicEmojis(): List<String> {
        return listOf(
            "🎵", "🎶", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸",
            "🪕", "🎻", "🎤", "🎧", "📻", "🎬", "🎭", "🎪",
            "🎨", "🎰", "🎲", "🎯", "🎳", "🎮", "🕹️", "🎴",
            "🎺", "🪘", "🎙️", "🎚️", "🎛️", "📀", "💿", "📱"
        )
    }
    
    /**
     * Returns heart emojis
     */
    private fun getHeartEmojis(): List<String> {
        return listOf(
            "💋", "💌", "💘", "💝", "💖", "💗", "💓", "💞",
            "💕", "💟", "❣️", "💔", "❤️", "🧡", "💛", "💚",
            "💙", "💜", "🤎", "🖤", "🤍", "💯", "💢", "💥",
            "❤️‍🔥", "❤️‍🩹", "💑", "💏", "👩‍❤️‍👨", "👨‍❤️‍👨", "👩‍❤️‍👩"
        )
    }
    
    /**
     * Returns a comprehensive list of commonly used emojis organized by category
     */
    private fun getEmojis(): List<String> {
        return listOf(
            // Smileys & Emotion
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
            "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐",
            "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
            "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒",
            "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "😶‍🌫️", "😵",
            "😵‍💫", "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐",
            "😕", "😟", "🙁", "☹️", "😮", "😯", "😲", "😳",
            "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭",
            "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱",
            "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️",
            
            // Hearts & Love
            "💋", "💌", "💘", "💝", "💖", "💗", "💓", "💞",
            "💕", "💟", "❣️", "💔", "❤️", "🧡", "💛", "💚",
            "💙", "💜", "🤎", "🖤", "🤍", "💯", "💢", "💥",
            
            // Gestures & Hands
            "👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️",
            "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉", "👆",
            "👇", "☝️", "👋", "🤚", "🖐️", "✋", "🖖", "👏",
            "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳",
            
            // Body Parts & People
            "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃",
            "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅",
            "👄", "👶", "🧒", "👦", "👧", "🧑", "👱", "👨",
            "🧔", "👨‍🦰", "👨‍🦱", "👨‍🦳", "👨‍🦲", "👩", "👩‍🦰", "🧑‍🦰",
            
            // Activities & Sports
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
            "🥏", "🎱", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏",
            "🥅", "⛳", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽",
            "⛸️", "🥌", "🛹", "🛼", "🏆", "🥇", "🥈", "🥉",
            
            // Music & Entertainment
            "🎵", "🎶", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸",
            "🪕", "🎻", "🎤", "🎧", "📻", "🎬", "🎭", "🎪",
            "🎨", "🎰", "🎲", "🎯", "🎳", "🎮", "🕹️", "🎴",
            
            // Food & Drink
            "🍕", "🍔", "🍟", "🌭", "🍿", "🧂", "🥓", "🥚",
            "🍳", "🧇", "🥞", "🧈", "🍞", "🥐", "🥨", "🥯",
            "🥖", "🧀", "🥗", "🥙", "🥪", "🌮", "🌯", "🫔",
            "🥫", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟",
            "🦪", "🍤", "🍙", "🍚", "🍘", "🍥", "🥠", "🥮",
            "🍢", "🍡", "🍧", "🍨", "🍦", "🥧", "🧁", "🍰",
            "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪",
            "🌰", "🥜", "🍯", "🥛", "🍼", "☕", "🍵", "🧃",
            "🥤", "🍶", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸",
            "🍹", "🧉", "🍾", "🧊", "🥄", "🍴", "🍽️", "🥢",
            
            // Animals & Nature
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
            "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉",
            "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛",
            "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🪳", "🦟",
            "🦗", "🕷️", "🕸️", "🦂", "🐢", "🐍", "🦎", "🦖",
            "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠",
            "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆",
            
            // Nature & Weather
            "🌸", "💐", "🌹", "🥀", "🌺", "🌻", "🌼", "🌷",
            "🌱", "🪴", "🌲", "🌳", "🌴", "🌵", "🌾", "🌿",
            "☘️", "🍀", "🍁", "🍂", "🍃", "🌍", "🌎", "🌏",
            "🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔",
            "🌙", "🌛", "🌜", "☀️", "🌝", "🌞", "⭐", "🌟",
            "💫", "✨", "☄️", "🌠", "🌌", "☁️", "⛅", "⛈️",
            "🌤️", "🌥️", "🌦️", "🌧️", "🌨️", "🌩️", "🌪️", "🌫️",
            "🌬️", "🌀", "🌈", "⚡", "❄️", "☃️", "⛄", "☔",
            
            // Travel & Places
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑",
            "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🏍️", "🛵",
            "🚲", "🛴", "✈️", "🛫", "🛬", "🚀", "🛸", "🚁",
            "⛵", "🚤", "🛳️", "⛴️", "🚢", "⚓", "🎢", "🎡",
            "🎠", "🏗️", "🗼", "🗽", "⛲", "⛱️", "🏖️", "🏝️",
            
            // Objects & Symbols
            "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "💾", "💿",
            "📱", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️",
            "🎚️", "🎛️", "🧭", "⏱️", "⏰", "⏲️", "⌚", "📡",
            "🔋", "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️",
            "💸", "💵", "💴", "💶", "💷", "🪙", "💰", "💳",
            "🎁", "🎀", "🎊", "🎉", "🎈", "🎏", "🎎", "🎐",
            
            // Symbols
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "❤️‍🔥", "❤️‍🩹", "💔", "❣️", "💕", "💞", "💓",
            "💗", "💖", "💘", "💝", "✔️", "✅", "❌", "❎",
            "➕", "➖", "✖️", "➗", "💯", "🔥", "⚡", "💥",
            "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "🟤", "⚫",
            "⚪", "🟥", "🟧", "🟨", "🟩", "🟦", "🟪", "🟫",
            "⬛", "⬜", "🔶", "🔷", "🔸", "🔹", "🔺", "🔻"
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): EmojiKeyboardDialog {
            return EmojiKeyboardDialog()
        }
    }
}

/**
 * RecyclerView adapter for displaying emoji grid
 */
class EmojiAdapter(
    private var emojis: List<String>,
    private val onEmojiClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    /**
     * Updates the emoji list and refreshes the view
     */
    fun updateEmojis(newEmojis: List<String>) {
        emojis = newEmojis
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emoji, parent, false)
        return EmojiViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        holder.bind(emojis[position])
    }

    override fun getItemCount(): Int = emojis.size

    inner class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emojiTextView: TextView = itemView.findViewById(R.id.emojiTextView)

        fun bind(emoji: String) {
            emojiTextView.text = emoji
            
            // Add ripple effect background
            itemView.setOnClickListener {
                // Add scale animation on click with bounce effect
                it.animate()
                    .scaleX(0.75f)
                    .scaleY(0.75f)
                    .setDuration(80)
                    .withEndAction {
                        it.animate()
                            .scaleX(1.1f)
                            .scaleY(1.1f)
                            .setDuration(80)
                            .withEndAction {
                                it.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(80)
                                    .withEndAction {
                                        onEmojiClick(emoji)
                                    }
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }
        }
    }
}
