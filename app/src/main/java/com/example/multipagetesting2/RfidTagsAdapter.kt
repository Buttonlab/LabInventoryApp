package com.example.multipagetesting2

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class RfidTagsAdapter(private val tags: ArrayList<CellItem>) : RecyclerView.Adapter<RfidTagsAdapter.tagViewHolder>() {

    val substitutions = DataRepository.substitutions

    inner class tagViewHolder(view: View): RecyclerView.ViewHolder(view) {
        var tag: TextView = view.findViewById(R.id.tagItem) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RfidTagsAdapter.tagViewHolder {
        return tagViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.tag_item, parent, false))
    }

    override fun onBindViewHolder(holder: RfidTagsAdapter.tagViewHolder, position: Int) {
        //val visibleText = tags[position].substring(0, (tags[position].length-4))
//        val textIn = tags[position]
//        val tagText = textIn.split(":", limit=2).last()
//        var textVisible: String
//        if (tagText.all { (it.isLetterOrDigit() || it.isWhitespace()) && it != '?' }) {
//            // Not sure what I was doing with this
//        }
//        if (tagText.first().equals('1') || tagText.first().equals('3')) {
//            try {
//
//                val genotype = substitutions?.subs?.get("genotype")?.get(tagText.substring(1,2)) ?: tagText.substring(1,2)
//                val distNum = tagText.substring(2,4).toInt(36)
//                val year = tagText.substring(4,6)
//                val owner = substitutions?.subs?.get("owner")?.get(tagText.substring(6,7)) ?: tagText.substring(6,7)
//                val passage = tagText.substring(7,8).toInt(36)
//                val surface = substitutions?.subs?.get("surface")?.get(tagText.substring(8,9)) ?: tagText.substring(8,9)
//                val number = tagText.substring(9,10).toInt(36)
//
//                textVisible = "$genotype$distNum$year   $owner\nOn:$surface   Psg#$passage   #$number"
//            } catch (e: Exception) {
//                textVisible = "$tagText   ERROR!"
//            }
//
//        } else if (tagText.first().equals('2') || tagText.first().equals('4')) {
//            try {
//                val cellType = substitutions?.subs?.get("cellType")?.get(tagText.substring(1,2)) ?: tagText.substring(1,2)
//                val genemod = substitutions?.subs?.get("genemod")?.get(tagText.substring(2,3)) ?: tagText.substring(2,3)
//                val gene1 = substitutions?.subs?.get("gene1")?.get(tagText.substring(3,4)) ?: tagText.substring(3,4)
//                val gene2 = substitutions?.subs?.get("gene2")?.get(tagText.substring(4,5)) ?: tagText.substring(4,5)
//                val resistance = substitutions?.subs?.get("resistance")?.get(tagText.substring(5,6)) ?: tagText.substring(5,6)
//                val clone = tagText.substring(6,8).toInt(16)
//                val passage = tagText.substring(8,10).toInt(16)
//                val number = tagText.substring(11,12).toInt(36)
//                var owner = ""
//                if (tagText.contains("_")) {
//                    owner = "   " + (substitutions?.subs?.get("owner")?.get(tagText.split("_").last()) ?: tagText.split("_").last())
//                }
//                textVisible = "$cellType    $genemod   $gene1   $gene2\n$resistance   Clone#$clone   Psg#${passage}   #${number}${owner}"
//            }catch (e: Exception) {
//                textVisible = "$tagText   ERROR!"
//            }
//        } else if (tagText.first().equals('5')) {
//            try {
//                val bacteriaType = substitutions?.subs?.get("cellType")?.get(tagText.substring(1,2)) ?: tagText.substring(1,2)
//                val bacteriaGenemod = substitutions?.subs?.get("genemod")?.get(tagText.substring(2,3)) ?: tagText.substring(2,3)
//                val gene1 = substitutions?.subs?.get("gene1")?.get(tagText.substring(3,4)) ?: tagText.substring(3,4)
//                val gene2 = substitutions?.subs?.get("gene2")?.get(tagText.substring(4,5)) ?: tagText.substring(4,5)
//                val bacteriaResistance = substitutions?.subs?.get("resistance")?.get(tagText.substring(5,6)) ?: tagText.substring(5,6)
//                val vectorResistance = substitutions?.subs?.get("resistance")?.get(tagText.substring(5,6)) ?: tagText.substring(5,6)
//                val clone = tagText.substring(7,9).toInt(16)
//                val number = tagText.substring(9,10).toInt(36)
//                var owner = ""
//                if (tagText.contains("_")) {
//                    owner = "   " + (substitutions?.subs?.get("owner")?.get(tagText.split("_").last()) ?: tagText.split("_").last())
//                }
//                textVisible = "$bacteriaType    $bacteriaGenemod   $gene1   $gene2\n$bacteriaResistance   $vectorResistance   Clone#$clone   #${number}${owner}"
//            }catch (e: Exception) {
//                textVisible = "$tagText   ERROR!"
//            }
//
//        } else {
//            textVisible = textIn
//        }
        val cell = tags[position]
        var textVisible: String
        if (cell.type.equals("1") || cell.type.equals("3")) {
            val genotype = substitutions?.subs?.get("genotype")?.get(cell.genotype) ?: cell.genotype ?: ""
            val distNum = cell.distNum?.toInt(36) ?: ""
            val year = cell.year ?: ""
            val owner = substitutions?.subs?.get("owner")?.get(cell.owner) ?: cell.owner ?: ""
            val passage = cell.passage?.toInt(36) ?: ""
            val surface = substitutions?.subs?.get("surface")?.get(cell.surface) ?: cell.surface ?: ""
            val number = cell.number?.toInt(36) ?: ""

            textVisible = "$genotype$distNum$year   $owner\nOn:$surface   Psg#$passage   #$number"
        } else if (cell.type.equals("2") || cell.type.equals("4")) {
            val cellType = substitutions?.subs?.get("cellType")?.get(cell.cellType) ?: cell.cellType ?: ""
            val genemod = substitutions?.subs?.get("genemod")?.get(cell.genemod) ?: cell.genemod ?: ""
            val gene1 = substitutions?.subs?.get("gene1")?.get(cell.gene1) ?: cell.gene1 ?: ""
            val gene2 = substitutions?.subs?.get("gene2")?.get(cell.gene2) ?: cell.gene2 ?: ""
            val resistance = substitutions?.subs?.get("resistance")?.get(cell.resistance) ?: cell.resistance ?: ""
            val clone = cell.clone?.toInt(16) ?: ""
            val passage = cell.passage?.toInt(16) ?: ""
            val number = cell.number?.toInt(36) ?: ""
            val owner = substitutions?.subs?.get("owner")?.get(cell.owner) ?: cell.owner ?: ""

            textVisible = "$cellType    $genemod   $gene1   $gene2\n$resistance  Clone#$clone  Psg#${passage}  #${number}  ${owner}"
        } else if (cell.type.equals("5")) {
            val otherType = substitutions?.subs?.get("cellType")?.get(cell.otherType) ?: cell.otherType ?: ""
            val otherGenemod = substitutions?.subs?.get("genemod")?.get(cell.otherGenemod) ?: cell.otherGenemod ?: ""
            val gene1 = substitutions?.subs?.get("gene1")?.get(cell.gene1) ?: cell.gene1 ?: ""
            val gene2 = substitutions?.subs?.get("gene2")?.get(cell.gene2) ?: cell.gene2 ?: ""
            val primaryResistance = substitutions?.subs?.get("resistance")?.get(cell.primaryResistance) ?: cell.primaryResistance ?: ""
            val vectorResistance = substitutions?.subs?.get("resistance")?.get(cell.vectorResistance) ?: cell.vectorResistance ?: ""
            val clone = cell.clone?.toInt(16) ?: ""
            val number = cell.number?.toInt(36) ?: ""
            val owner = substitutions?.subs?.get("owner")?.get(cell.owner) ?: cell.owner ?: ""

            textVisible = "$otherType    $otherGenemod   $gene1   $gene2\n$primaryResistance  $vectorResistance  Clone#$clone  #${number}  ${owner}"
        } else if (cell.type.equals("6")) {
            val name = cell.name ?: cell.id

            textVisible = "Basic Item:\n${name}"
        } else if (cell.type.equals("7")) {
            val cellType = substitutions?.subs?.get("cellType")?.get(cell.cellType) ?: cell.cellType ?: ""
            val source = substitutions?.subs?.get("source")?.get(cell.source) ?: cell.source ?: ""
            val genemod = substitutions?.subs?.get("genemod")?.get(cell.genemod) ?: cell.genemod ?: ""
            val gene1 = substitutions?.subs?.get("gene1")?.get(cell.gene1) ?: cell.gene1 ?: ""
            val gene2 = substitutions?.subs?.get("gene2")?.get(cell.gene2) ?: cell.gene2 ?: ""
            val media = substitutions?.subs?.get("media")?.get(cell.media) ?: cell.media ?: ""
            val supplements = cell.clone?.toInt(16) ?: ""
            val owner = substitutions?.subs?.get("owner")?.get(cell.owner) ?: cell.owner ?: ""

            textVisible = "$cellType    $genemod   $gene1   $gene2\n$source    $media    $supplements    ${owner}"
        } else {
            textVisible = "ERROR: Invalid type"
        }


        holder.tag.text = textVisible
        holder.tag.tooltipText = reprCell(cell)
        holder.tag.setBackgroundResource(R.drawable.inv_item)

    }

    override fun getItemCount(): Int {
        return tags.size
    }

    fun updateData(newTags: ArrayList<CellItem>) {
        tags.clear()
        notifyDataSetChanged()
        tags.addAll(newTags)
        notifyDataSetChanged()

    }

    fun removeItem(tag: CellItem) {
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