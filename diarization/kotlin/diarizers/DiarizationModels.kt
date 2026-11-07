package com.nuclearhack.diarization

/**
 * Один сегмент диаризации: кто говорил и когда.
 *
 * [start] и [end] — секунды от начала файла, [speaker] — номер кластера
 * (0..K-1). Номера не несут смысла: «speaker_1» в отчёте — это первый
 * по времени кластер, а не конкретный человек.
 */
data class DiarizationSegment(
    val start: Float,
    val end: Float,
    val speaker: Int,
    val confidence: Float = 0f,
) {
    init {
        require(end >= start) { "end ($end) должен быть >= start ($start)" }
        require(speaker >= 0) { "speaker должен быть >= 0" }
    }

    val duration: Float get() = end - start
}

/**
 * Результат диаризации файла.
 *
 * [speakerCount] — сколько спикеров нашла модель. В режиме с автоматическим
 * определением это оценка (см. [estimatedSpeakerCount] = true), в режиме
 * с явным числом — ровно переданное значение.
 */
data class DiarizationResult(
    val segments: List<DiarizationSegment>,
    val speakerCount: Int,
    val estimatedSpeakerCount: Boolean = false,
    val timingsMs: Map<String, Long> = emptyMap(),
) {
    init {
        require(speakerCount >= 0) { "speakerCount должен быть >= 0" }
    }

    /** Все номера спикеров, которые реально встречаются в сегментах. */
    val speakers: List<Int>
        get() = segments.map { it.speaker }.distinct().sorted()

    /**
     * Стабильные человекочитаемые метки: `speaker_1` — тот, кто заговорил
     * первым, и т.д. Нужны для JSON и для склейки реплик.
     */
    fun speakerLabels(): Map<Int, String> {
        val order = segments.sortedBy { it.start }.map { it.speaker }.distinct()
        val labels = LinkedHashMap<Int, String>()
        order.forEachIndexed { index, spk -> labels[spk] = "speaker_${index + 1}" }
        for (spk in speakers) {
            labels.getOrPut(spk) { "speaker_${labels.size + 1}" }
        }
        return labels
    }

    /** Найти спикера в момент [time] (секунды); `null`, если речи нет. */
    fun speakerAt(time: Float): Int? =
        segments.firstOrNull { time >= it.start && time < it.end }?.speaker
}
