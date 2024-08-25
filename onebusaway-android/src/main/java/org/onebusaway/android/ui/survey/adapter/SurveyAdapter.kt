package org.onebusaway.android.ui.survey.adapter

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.onebusaway.android.R
import org.onebusaway.android.io.request.survey.model.StudyResponse
import org.onebusaway.android.ui.survey.utils.SurveyUtils
import org.onebusaway.android.ui.survey.utils.SurveyViewUtils


class SurveyAdapter(
    private val context: Context,
    private val surveyQuestions: MutableList<StudyResponse.Surveys.Questions>
) : RecyclerView.Adapter<SurveyAdapter.SurveyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_survey, parent, false)
        return SurveyViewHolder(view)
    }

    override fun onBindViewHolder(holder: SurveyViewHolder, position: Int) {
        holder.setIsRecyclable(false);
        val surveyQuestion = surveyQuestions[position]
        holder.bind(surveyQuestion)
    }

    override fun getItemCount(): Int = surveyQuestions.size

    inner class SurveyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val surveyQuestionTv: TextView = itemView.findViewById(R.id.survey_question_tv)
        private val editText: EditText = itemView.findViewById(R.id.editText)
        private val radioGroup: RadioGroup = itemView.findViewById(R.id.radioGroup)
        private val checkBoxContainer: LinearLayout = itemView.findViewById(R.id.checkBoxContainer)
        private val surveyCard: CardView = itemView.findViewById(R.id.surveyCard)
        private val checkBoxLabel: TextView = itemView.findViewById(R.id.checkBoxLabel)
        fun bind(surveyQuestion: StudyResponse.Surveys.Questions) {

            checkBoxLabel.visibility = View.GONE
            surveyCard.setCardBackgroundColor(
                ContextCompat.getColor(
                    context, R.color.quantum_white_100
                )
            )
            when (surveyQuestion.content.type) {
                SurveyUtils.TEXT_QUESTION -> {
                    SurveyViewUtils.showTextInputQuestion(itemView, surveyQuestion)
                    setVisibility(
                        editText,
                        radioGroup,
                        checkBoxContainer,
                        editTextVisible = true,
                        radioGroupVisible = false,
                        checkBoxVisible = false
                    )
                }

                SurveyUtils.RADIO_BUTTON_QUESTION -> {
                    Log.d("Radio", adapterPosition.toString());
                    SurveyViewUtils.showRadioGroupQuestion(context, itemView, surveyQuestion)
                    setVisibility(
                        editText,
                        radioGroup,
                        checkBoxContainer,
                        editTextVisible = false,
                        radioGroupVisible = true,
                        checkBoxVisible = false
                    )
                }

                SurveyUtils.CHECK_BOX_QUESTION -> {
                    SurveyViewUtils.showCheckBoxQuestion(context, itemView, surveyQuestion)
                    setVisibility(
                        editText,
                        radioGroup,
                        checkBoxContainer,
                        editTextVisible = false,
                        radioGroupVisible = false,
                        checkBoxVisible = true
                    )
                }

                SurveyUtils.LABEL -> {
                    surveyCard.setCardBackgroundColor(
                        ContextCompat.getColor(
                            context, R.color.quantum_yellow50
                        )
                    )
                    setVisibility(
                        editText,
                        radioGroup,
                        checkBoxContainer,
                        editTextVisible = false,
                        radioGroupVisible = false,
                        checkBoxVisible = false
                    )
                }
            }
            val questionNumber = adapterPosition + 1
            val questionText = surveyQuestion.content.label_text
            surveyQuestionTv.text = "$questionNumber. $questionText"
            handleOnSelectionEvents(
                radioGroup,
                editText,
                checkBoxContainer,
                adapterPosition,
                itemView,
                surveyQuestion.content.type
            )
        }
    }

    fun handleOnSelectionEvents(
        radioGroup: RadioGroup,
        textLabel: EditText,
        checkBoxContainer: LinearLayout,
        position: Int,
        itemView: View,
        type: String
    ) {

        when (type) {
            SurveyUtils.TEXT_QUESTION -> {
                textLabel.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        saveQuestionAnswer(position, itemView)
                    }

                    override fun beforeTextChanged(
                        s: CharSequence?, start: Int, count: Int, after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?, start: Int, before: Int, count: Int
                    ) {
                    }
                })
            }

            SurveyUtils.RADIO_BUTTON_QUESTION -> {
                radioGroup.setOnCheckedChangeListener { _, _ ->
                    Log.d("position", position.toString() + " " + "radio")
                    saveQuestionAnswer(position, itemView)
                }
            }

            SurveyUtils.CHECK_BOX_QUESTION -> {
                for (i in 0 until checkBoxContainer.childCount) {
                    val checkBox = checkBoxContainer.getChildAt(i) as CheckBox
                    checkBox.setOnCheckedChangeListener { _, _ ->
                        saveQuestionAnswer(
                            position, itemView
                        )
                    }
                }
            }
        }


    }

    private fun saveQuestionAnswer(position: Int, holder: View) {
        val questionType = surveyQuestions[position].content.type
        Log.d("Survey Adapter", "Question Clicked");
        when (questionType) {
            SurveyUtils.TEXT_QUESTION -> {
                surveyQuestions[position].questionAnswer = SurveyUtils.getTextInputAnswer(holder)
            }

            SurveyUtils.RADIO_BUTTON_QUESTION -> {
                surveyQuestions[position].questionAnswer =
                    SurveyUtils.getSelectedRadioButtonAnswer(holder)
            }

            SurveyUtils.CHECK_BOX_QUESTION -> {
                surveyQuestions[position].multipleAnswer =
                    SurveyUtils.getSelectedCheckBoxAnswer(holder)
            }

        }
    }


    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItemViewType(position: Int): Int {
        return position
    }

    fun setVisibility(
        editText: View,
        radioGroup: View,
        checkBox: View,
        editTextVisible: Boolean,
        radioGroupVisible: Boolean,
        checkBoxVisible: Boolean
    ) {
        editText.visibility = if (editTextVisible) View.VISIBLE else View.GONE
        radioGroup.visibility = if (radioGroupVisible) View.VISIBLE else View.GONE
        checkBox.visibility = if (checkBoxVisible) View.VISIBLE else View.GONE
    }
}
