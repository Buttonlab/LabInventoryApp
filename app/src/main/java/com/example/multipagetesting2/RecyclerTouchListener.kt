package com.example.multipagetesting2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class RecyclerTouchListener(context: Context, recyclerView: RecyclerView, private val clickListener: ClickListener): RecyclerView.OnItemTouchListener {

    private val gestureDetector: GestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val child = recyclerView.findChildViewUnder(e.x, e.y)
            if (child != null) {
                clickListener.onLongClick(child, recyclerView.getChildAdapterPosition(child))
            }
        }
    })

    interface ClickListener {
        fun onClick(view: View, position: Int)

        fun onLongClick(view: View, position: Int)
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        val child = rv.findChildViewUnder(e.x, e.y)
        if (child != null && gestureDetector.onTouchEvent(e)) {
            try {
                val textView: TextView = child.findViewById(R.id.tagItem)
                val textToCopy = textView.tooltipText.toString().split(":", limit=2)[1]
                val clipboard = rv.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("RFID tag", textToCopy)
                clipboard.setPrimaryClip(clip)
                clickListener.onClick(child, rv.getChildAdapterPosition(child))
            } catch (e: Exception) {
                val textView: TextView = child.findViewById(R.id.tagItem)
                val textToCopy = textView.tooltipText.toString()
                val clipboard = rv.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("RFID tag", textToCopy)
                clipboard.setPrimaryClip(clip)
                clickListener.onClick(child, rv.getChildAdapterPosition(child))
            }

        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}