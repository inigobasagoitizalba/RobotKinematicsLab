package com.robotkinematicslab.mobile.ui.training

import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TrainingControlsDraftTest {
    @Test fun customAndIntermediateInvalidEditsReopenPerProjectWithoutBecomingExecutable() {
        val root = Files.createTempDirectory("training-draft").toFile()
        try {
            val a = TrainingControlsDraftRepository(File(root,"a/controls.properties"))
            val b = TrainingControlsDraftRepository(File(root,"b/controls.properties"))
            val custom = TrainingControlsDraft(model=TrainingModelKind.COMPACT_MLP,split=TrainingSplitStrategy.ROBOT_HELD_OUT,hidden="37",rate="0.004",seed="-7")
            assertTrue(custom.errors().isEmpty());assertTrue(custom.customized)
            a.save(custom); b.save(TrainingControlsDraft())
            assertEquals(custom,a.load());assertEquals(TrainingControlsDraft(),b.load())
            val unfinished = custom.copy(epochs="",rate="NaN",seed="2147483648")
            a.save(unfinished)
            assertEquals(unfinished,a.load())
            assertEquals(setOf("epochs","learningRate","seed"),requireNotNull(a.load()).errors().keys)
            assertEquals(TrainingControlsDraft(),b.load())
        } finally { root.deleteRecursively() }
    }
    @Test fun corruptOrOversizedDraftIsRejectedWithoutChangingDisk() {
        val root = Files.createTempDirectory("training-draft-corrupt").toFile()
        try {
            val file = File(root,"controls.properties")
            val repository = TrainingControlsDraftRepository(file)
            repository.save(TrainingControlsDraft())
            val properties = java.util.Properties().apply { file.inputStream().use(::load) }
            properties.remove("hidden")
            file.outputStream().use { properties.store(it,"corrupt") }
            val bytes=file.readBytes()
            assertThrows(IllegalArgumentException::class.java) { repository.load() }
            assertArrayEquals(bytes,file.readBytes())
            assertThrows(IllegalArgumentException::class.java) { repository.save(TrainingControlsDraft(epochs="1".repeat(129))) }
            assertArrayEquals(bytes,file.readBytes())
        } finally { root.deleteRecursively() }
    }
}
