package nl.deschepers.laraveltinker.listener

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import nl.deschepers.laraveltinker.Strings
import nl.deschepers.laraveltinker.settings.GlobalSettingsState
import nl.deschepers.laraveltinker.settings.PatreonSupport
import nl.deschepers.laraveltinker.settings.PersistentApplicationCache
import nl.deschepers.laraveltinker.toolwindow.TinkerOutputToolWindowFactory

class PhpProcessListener(private val project: Project, private val processHandler: ProcessHandler) :
    ProcessListener {

    companion object {
        private const val OUTPUT_START_SEQUENCE = "%%START-OUTPUT%%"
        private const val OUTPUT_END_SEQUENCE = "%%END-OUTPUT%%"
        private const val OUTPUT_EOT_PROMPT = "%%EOT%%"
        private const val SUPPORT_MESSAGE_EXECUTIONS = 10
    }

    private val processOutput = ArrayList<String>()

    private var capturing = false
    private var firstLine = true

    override fun startNotified(event: ProcessEvent) {
        if (!PatreonSupport.hasValidKey()) {
            PersistentApplicationCache.instance.state.executionsCount++
        }
        TinkerOutputToolWindowFactory.tinkerOutputToolWindow[project]?.plug = false
    }

    override fun processTerminated(event: ProcessEvent) {
        ApplicationManager.getApplication()
            .invokeLater(
                {
                    val pluginSettings = GlobalSettingsState.getInstance()

                    if (pluginSettings.showExecutionEnded) {
                        TinkerOutputToolWindowFactory.tinkerOutputToolWindow[project]
                            ?.addOutput(Strings.get("lt.execution.finished"))
                    }

                    if (PersistentApplicationCache.instance.state.executionsCount >=
                        SUPPORT_MESSAGE_EXECUTIONS && !PatreonSupport.hasValidKey()
                    ) {
                        TinkerOutputToolWindowFactory.tinkerOutputToolWindow[project]?.plug = true
                        PersistentApplicationCache.instance.state.executionsCount = 0
                    }
                },
                ModalityState.NON_MODAL
            )
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (firstLine) {
            firstLine = false
            return
        }

        var capText = event.text

        if (capText == OUTPUT_EOT_PROMPT) {
            processHandler.destroyProcess()
        }

        if (!capturing && capText.contains(OUTPUT_START_SEQUENCE)) {
            capText =
                capText.substring(
                    capText.indexOf(OUTPUT_START_SEQUENCE) + OUTPUT_START_SEQUENCE.length
                )
            capturing = true
        }
        if (capturing && capText.contains(OUTPUT_END_SEQUENCE)) {
            capText = capText.substring(0, capText.indexOf(OUTPUT_END_SEQUENCE))
            processOutput.add(capText)
            capturing = false
        }

        if (capturing) {
            processOutput.add(capText)
            ApplicationManager.getApplication()
                .invokeLater(
                    {
                        TinkerOutputToolWindowFactory.tinkerOutputToolWindow[project]
                            ?.addOutput(capText)
                    },
                    ModalityState.NON_MODAL
                )
        }
    }
}
