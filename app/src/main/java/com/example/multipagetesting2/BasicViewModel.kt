package com.example.multipagetesting2
import androidx.lifecycle.ViewModel

class BasicViewModel: ViewModel() {
    // This is used to hold and send basic data between the fragments

    private var selectedTag: String

    init {
        selectedTag = ""
    }

    fun setSelectedTag(tag: String?) {
        if (!tag.isNullOrEmpty()) {
            selectedTag = tag
        }
    }

    fun getSelectedTag(): String {
        return selectedTag
    }
}