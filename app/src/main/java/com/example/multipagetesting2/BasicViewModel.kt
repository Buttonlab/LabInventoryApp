package com.example.multipagetesting2
import androidx.lifecycle.ViewModel

class BasicViewModel: ViewModel() {
    // This is used to hold and send basic data between the fragments

    private var _selectedTag: String = ""
    var selectedTag: String
        get() = _selectedTag
        set(value) {
            if (value.isNotEmpty()) {
                _selectedTag = value
            }
        }

    private var _writeTarget: String = ""
    var writeTarget: String
        get() = _writeTarget
        set(value) {
            _writeTarget = value.trim()
        }

    private var _writeItem: CellItem? = null
    var writeItem: CellItem?
        get() = _writeItem
        set(value) {
            _writeItem = value
        }
}