package apptentive.com.android.feedback.notes.viewmodel

import apptentive.com.android.core.Callback
import apptentive.com.android.feedback.EngagementResult
import apptentive.com.android.feedback.INTERACTIONS
import apptentive.com.android.feedback.engagement.EngagementContext
import apptentive.com.android.feedback.engagement.Event
import apptentive.com.android.feedback.notes.interaction.TextModalInteraction
import apptentive.com.android.util.Log

class TextModalViewModel(
    private val context: EngagementContext,
    private val interaction: TextModalInteraction
) {
    val title = interaction.title
    val message = interaction.body

    val actions = interaction.actions.mapIndexed { index, action ->
        ActionModel(
            title = action.label,
            callback = {
                // invoke action
                context.executors.state.execute(createActionCallback(action, index))

                // dismiss UI
                onDismiss?.invoke()
            }
        )
    }

    var onDismiss: Callback? = null

    fun launch() {
        context.executors.state.execute {
            engageCodePoint(CODE_POINT_LAUNCH)
        }
    }

    fun cancel() {
        context.executors.state.execute {
            engageCodePoint(CODE_POINT_CANCEL)
        }
    }

    private fun engageCodePoint(codePoint: String, data: Map<String, Any?>? = null) {
        context.engage(
            event = Event.internal(codePoint, interaction = "TextModal"),
            interactionId = interaction.id,
            data = data
        )
    }

    private fun createActionCallback(action: TextModalInteraction.Action, index: Int): Callback =
        when (action) {
            is TextModalInteraction.Action.Dismiss -> {
                {
                    // engage event
                    val data = createEventData(action, index)
                    engageCodePoint(CODE_POINT_DISMISS, data)
                }
            }
            is TextModalInteraction.Action.Invoke -> {
                {
                    // run invocation
                    val result = context.engage(action.invocations)
                    if (result !is EngagementResult.Success) {
                        Log.e(INTERACTIONS, "No runnable interactions") // TODO: better message
                    }

                    // engage event
                    val data = createEventData(action, index, result)
                    engageCodePoint(CODE_POINT_INTERACTION, data)
                }
            }
            is TextModalInteraction.Action.Event -> {
                {
                    // engage target event
                    val result = context.engage(
                        event = action.event,
                        interactionId = interaction.id
                    )
                    if (result !is EngagementResult.Success) {
                        Log.e(INTERACTIONS, "No runnable interactions") // TODO: better message
                    }

                    // engage event
                    val data = createEventData(action, index, result)
                    engageCodePoint(CODE_POINT_EVENT, data)
                }
            }
            else -> {
                throw IllegalArgumentException("Unexpected action: $action")
            }
        }

    data class ActionModel(val title: String, val callback: Callback) {
        operator fun invoke() {
            callback.invoke()
        }
    }

    companion object {
        const val CODE_POINT_INTERACTION = "interaction"
        const val CODE_POINT_EVENT = "event"
        const val CODE_POINT_DISMISS = "dismiss"
        const val CODE_POINT_CANCEL = "cancel"
        const val CODE_POINT_LAUNCH = "launch"

        private const val DATA_ACTION_ID = "action_id"
        private const val DATA_ACTION_LABEL = "label"
        private const val DATA_ACTION_POSITION = "position"
        private const val DATA_ACTION_INTERACTION_ID = "invoked_interaction_id"

        private fun createEventData(
            action: TextModalInteraction.Action,
            actionPosition: Int,
            engagementResult: EngagementResult? = null
        ): Map<String, Any?> {
            // we need to include a target interaction id (if any)
            if (engagementResult != null) {
                val interactionId = (engagementResult as? EngagementResult.Success)?.interactionId
                return mapOf(
                    DATA_ACTION_ID to action.id,
                    DATA_ACTION_LABEL to action.label,
                    DATA_ACTION_POSITION to actionPosition,
                    DATA_ACTION_INTERACTION_ID to interactionId
                )
            }

            return mapOf(
                DATA_ACTION_ID to action.id,
                DATA_ACTION_LABEL to action.label,
                DATA_ACTION_POSITION to actionPosition
            )
        }
    }
}