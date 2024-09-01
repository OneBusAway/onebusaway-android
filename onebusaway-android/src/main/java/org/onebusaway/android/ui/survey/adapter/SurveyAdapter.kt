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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.google.api.Distribution.BucketOptions.Linear
import kotlinx.android.synthetic.main.item_survey.view.radioGroup
import org.onebusaway.android.R
import org.onebusaway.android.ui.survey.SurveyUtils
import org.onebusaway.android.io.request.survey.model.StudyResponse


class SurveyAdapter(
    private val context: Context,
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
        private val checkBoxContainer: LinearLayout = itemView.findViewById(R.id.checkBoxContainer)
        private val surveyCard: CardView = itemView.findViewById(R.id.surveyCard)
        private val checkBoxLabel: TextView = itemView.findViewById(R.id.checkBoxLabel)
        private val container: ConstraintLayout = itemView.findViewById(R.id.container)

        fun bind(surveyQuestion: StudyResponse.Surveys.Questions) {

            surveyQuestionTv.text = surveyQuestion.content.label_text
            checkBoxLabel.visibility = View.GONE
            surveyCard.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    R.color.quantum_white_100
                )
            )
            when (surveyQuestion.content.type) {
                "text" -> {
                    SurveyUtils.showTextInputQuestion(context, itemView, surveyQuestion)
                    setVisibility(
                        editText,
                        radioGroup,
                        checkBoxContainer,
                        editTextVisible = true,
                        radioGroupVisible = false,
                        checkBoxVisible = false
                    )
                }

                "radio" -> {
                    Log.d("Radio", adapterPosition.toString());
                    SurveyUtils.showRadioGroupQuestion(context, itemView, surveyQuestion)
                    setVisibility(
                        editText,
                        radioGroup,
                        checkBoxContainer,
                        editTextVisible = false,
                        radioGroupVisible = true,
                        checkBoxVisible = false
                    )
                }

                "checkbox" -> {
                    SurveyUtils.showCheckBoxQuestion(context, itemView, surveyQuestion)
                    setVisibility(
                        editText,
                        radioGroup,
                        checkBoxContainer,
                        editTextVisible = false,
                        radioGroupVisible = false,
                        checkBoxVisible = true
                    )
                }

                "label" -> {
                    surveyCard.setCardBackgroundColor(
                        ContextCompat.getColor(
                            context,
                            R.color.quantum_yellow50
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
            handleOnSelectionEvents(radioGroup, editText, checkBoxContainer, adapterPosition, itemView, surveyQuestion.content.type)
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
            "text" -> {
                textLabel.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        saveQuestionAnswer(position, itemView)
                        Log.e("position", position.toString() + " " + "label")
                    }

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }
                })
            }

            "radio" -> {
                radioGroup.setOnCheckedChangeListener { _, _ ->
                    Log.e("position", position.toString() + " " + "radio")
                    saveQuestionAnswer(position, itemView)
                }
            }

            "checkbox" -> {
                for (i in 0 until checkBoxContainer.childCount) {
                    val checkBox = checkBoxContainer.getChildAt(i) as CheckBox
                    checkBox.setOnCheckedChangeListener { _, _ ->
                        saveQuestionAnswer(
                            position,
                            itemView
                        )
                    }
                }
            }
        }


    }

    private fun saveQuestionAnswer(position: Int, holder: View) {
        val questionType = surveyQuestions[position].content.type
        Log.d("QuestionClicked", "test");
        when (questionType) {
            "text" -> {
                surveyQuestions[position].questionAnswer = SurveyUtils.getTextInputAnswer(holder)
            }

            "radio" -> {
                surveyQuestions[position].questionAnswer =
                    SurveyUtils.getSelectedRadioButtonAnswer(holder)
            }

            "checkbox" -> {
                surveyQuestions[position].multipleAnswer =
                    SurveyUtils.getSelectedCheckBoxAnswer(holder)
            }

        }
    }


    override fun getItemId(position: Int): Long = position.toLong()


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
