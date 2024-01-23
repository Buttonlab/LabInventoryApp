package com.example.multipagetesting2

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

val substitutions = DataRepository.substitutions

class RfidTagsAdapter(private val tags: ArrayList<String>) : RecyclerView.Adapter<RfidTagsAdapter.tagViewHolder>() {

    inner class tagViewHolder(view: View): RecyclerView.ViewHolder(view) {
        var tag: TextView = view.findViewById(R.id.tagItem) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RfidTagsAdapter.tagViewHolder {
        return tagViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.tag_item, parent, false))
    }

    override fun onBindViewHolder(holder: RfidTagsAdapter.tagViewHolder, position: Int) {
        //val visibleText = tags[position].substring(0, (tags[position].length-4))
        val textIn = tags[position]
        val tagText = textIn.split(":", limit=2).last()
        var textVisible: String
        if (tagText.all { (it.isLetterOrDigit() || it.isWhitespace()) && it != '?' }) {
            // Not sure what I was doing with this
        }
        if (tagText.first().equals('1') || tagText.first().equals('3')) {
            try {

                val genotype = substitutions?.subs?.get("genotype")?.get(tagText.substring(1,2)) ?: tagText.substring(1,2)
                val distNum = tagText.substring(2,4).toInt(36)
                val owner = substitutions?.subs?.get("owner")?.get(tagText.substring(6,7)) ?: tagText.substring(6,7)
                val passage = tagText.substring(7,8).toInt(36)
                val surface = substitutions?.subs?.get("surface")?.get(tagText.substring(8,9)) ?: tagText.substring(8,9)
                val number = tagText.substring(9,10).toInt(36)

                textVisible = "$genotype$distNum   $owner\nOn:$surface   Psg#$passage   #$number"
            } catch (e: Exception) {
                textVisible = "$tagText   ERROR!"
            }

        } else if (tagText.first().equals('2') || tagText.first().equals('4')) {
            try {
                val cellType = substitutions?.subs?.get("cellType")?.get(tagText.substring(1,2)) ?: tagText.substring(1,2)
                val genemod = substitutions?.subs?.get("genemod")?.get(tagText.substring(2,3)) ?: tagText.substring(2,3)
                val gene1 = substitutions?.subs?.get("gene1")?.get(tagText.substring(3,4)) ?: tagText.substring(3,4)
                val gene2 = substitutions?.subs?.get("gene2")?.get(tagText.substring(4,5)) ?: tagText.substring(4,5)
                val resistance = substitutions?.subs?.get("resistance")?.get(tagText.substring(5,6)) ?: tagText.substring(5,6)
                val clone = tagText.substring(6,8).toInt(16)
                val passage = tagText.substring(8,10).toInt(16)
                val number = tagText.substring(11,12).toInt(36)
                textVisible = "$cellType    $genemod   $gene1   $gene2   \n$resistance   Clone#$clone   Psg#${passage}   #${number}"
            }catch (e: Exception) {
                textVisible = "$tagText   ERROR!"
            }

        } else {
            textVisible = textIn
        }
        holder.tag.text = textVisible
        holder.tag.tooltipText = tagText
        holder.tag.setBackgroundResource(R.drawable.inv_item)

    }

    override fun getItemCount(): Int {
        return tags.size
    }

    fun updateData(newTags: ArrayList<String>) {
        tags.clear()
        notifyDataSetChanged()
        tags.addAll(newTags)
        notifyDataSetChanged()

    }

    fun removeItem(tag: String) {
        val tagPos = tags.indexOf(tag)
        if (tagPos != -1) {
            tags.removeAt(tagPos)
            notifyItemRemoved(tagPos)
        }
    }

    fun clearData() {
        tags.clear()
        notifyDataSetChanged()
    }

}