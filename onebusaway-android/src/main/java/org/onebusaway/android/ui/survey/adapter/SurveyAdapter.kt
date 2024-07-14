package org.onebusaway.android.ui.survey.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.onebusaway.android.R
import org.onebusaway.android.ui.survey.SurveyUtils
import org.onebusaway.android.ui.survey.model.StudyResponse


class SurveyAdapter(
    private val context:Context,
    private val surveyQuestions: MutableList<StudyResponse.Surveys.Questions>
) : RecyclerView.Adapter<SurveyAdapter.SurveyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_survey, parent, false)
        return SurveyViewHolder(view)
    }

    override fun onBindViewHolder(holder: SurveyViewHolder, position: Int) {
        val surveyQuestion = surveyQuestions[position]
        holder.bind(surveyQuestion)
    }

    override fun getItemCount(): Int = surveyQuestions.size

    inner class SurveyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val surveyQuestionTv: TextView = itemView.findViewById(R.id.survey_question_tv)
        private val editText: EditText = itemView.findViewById(R.id.editText)
        private val radioGroup: RadioGroup = itemView.findViewById(R.id.radioGroup)
        private val checkBox: LinearLayout = itemView.findViewById(R.id.checkBoxContainer)
        private val surveyCard : CardView = itemView.findViewById(R.id.surveyCard)
        private val checkBoxLabel : TextView = itemView.findViewById(R.id.checkBoxLabel)

        fun bind(surveyQuestion: StudyResponse.Surveys.Questions) {

            surveyQuestionTv.text = surveyQuestion.content.label_text
            checkBoxLabel.visibility = View.GONE
            when (surveyQuestion.content.type) {
                "text" -> {
                    SurveyUtils.showTextInputQuestion(context, itemView, surveyQuestion)
                    setVisibility(editText, radioGroup, checkBox, editTextVisible = true, radioGroupVisible = false, checkBoxVisible = false)
                }
                "radio" -> {
                    SurveyUtils.showRadioGroupQuestion(context, itemView, surveyQuestion)
                    setVisibility(editText, radioGroup, checkBox, editTextVisible = false, radioGroupVisible = true, checkBoxVisible = false)
                }
                "checkbox" -> {
                    SurveyUtils.showCheckBoxQuestion(context, itemView, surveyQuestion)
                    setVisibility(editText, radioGroup, checkBox, editTextVisible = false, radioGroupVisible = false, checkBoxVisible = true)
                }
                "label" -> {
                    surveyCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.quantum_yellow50))
                    setVisibility(editText, radioGroup, checkBox, editTextVisible = false, radioGroupVisible = false, checkBoxVisible = false)
                }
            }

        }
    }
    fun setVisibility(editText: View, radioGroup: View, checkBox: View, editTextVisible: Boolean, radioGroupVisible: Boolean, checkBoxVisible: Boolean) {
        editText.visibility = if (editTextVisible) View.VISIBLE else View.GONE
        radioGroup.visibility = if (radioGroupVisible) View.VISIBLE else View.GONE
        checkBox.visibility = if (checkBoxVisible) View.VISIBLE else View.GONE
    }
}
