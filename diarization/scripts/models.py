"""Registry of segmentation and speaker-embedding models used by the benchmark."""
from __future__ import annotations

import os
from dataclasses import dataclass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS_DIR = os.path.join(ROOT, "models")


@dataclass(frozen=True)
class SegmentationModel:
    id: str
    path: str
    license: str
    note: str = ""


@dataclass(frozen=True)
class EmbeddingModel:
    id: str
    file: str
    dim: int
    license: str
    note: str = ""

    @property
    def path(self) -> str:
        return os.path.join(MODELS_DIR, self.file)

    @property
    def size_mb(self) -> float:
        return os.path.getsize(self.path) / 1e6


SEGMENTATION = {
    "pyannote-3.0": SegmentationModel(
        id="pyannote-3.0",
        path=os.path.join(MODELS_DIR, "sherpa-onnx-pyannote-segmentation-3-0", "model.int8.onnx"),
        license="MIT",
        note="pyannote/segmentation-3.0, int8, 7-class powerset, 10 s windows",
    ),
    "reverb-v1": SegmentationModel(
        id="reverb-v1",
        path=os.path.join(MODELS_DIR, "sherpa-onnx-reverb-diarization-v1", "model.int8.onnx"),
        license="Non-commercial (Rev.ai)",
        note="Revai/reverb-diarization-v1; comparison only",
    ),
}

EMBEDDING = {
    "campplus_zh_en": EmbeddingModel(
        "campplus_zh_en",
        "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
        192,
        "Apache-2.0",
        "3D-Speaker CAM++, trained on Chinese+English; fastest good model",
    ),
    "campplus_en": EmbeddingModel(
        "campplus_en",
        "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx",
        512,
        "Apache-2.0",
        "3D-Speaker CAM++, English VoxCeleb",
    ),
    "eres2netv2_zh": EmbeddingModel(
        "eres2netv2_zh",
        "3dspeaker_speech_eres2netv2_sv_zh-cn_16k-common.onnx",
        192,
        "Apache-2.0",
        "ERes2NetV2, Chinese common; best ARI on AMI in our tests",
    ),
    "eres2net_base_zh": EmbeddingModel(
        "eres2net_base_zh",
        "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
        512,
        "Apache-2.0",
        "ERes2Net base, Chinese 3D-Speaker",
    ),
    "eres2net_en": EmbeddingModel(
        "eres2net_en",
        "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx",
        192,
        "Apache-2.0",
        "ERes2Net, English VoxCeleb",
    ),
    "titanet_small": EmbeddingModel(
        "titanet_small",
        "nemo_en_titanet_small.onnx",
        192,
        "NVIDIA NeMo (see model card)",
        "TitaNet small, English",
    ),
    "titanet_large": EmbeddingModel(
        "titanet_large",
        "nemo_en_titanet_large.onnx",
        192,
        "NVIDIA NeMo (see model card)",
        "TitaNet large, English",
    ),
    "wespeaker_campplus": EmbeddingModel(
        "wespeaker_campplus",
        "wespeaker_en_voxceleb_CAM++_LM.onnx",
        512,
        "Apache-2.0",
        "WeSpeaker CAM++ large-margin, English VoxCeleb",
    ),
    "wespeaker_resnet34": EmbeddingModel(
        "wespeaker_resnet34",
        "wespeaker_en_voxceleb_resnet34_LM.onnx",
        256,
        "Apache-2.0",
        "WeSpeaker ResNet34 large-margin, English VoxCeleb",
    ),
    "wespeaker_resnet152": EmbeddingModel(
        "wespeaker_resnet152",
        "wespeaker_en_voxceleb_resnet152_LM.onnx",
        256,
        "Apache-2.0",
        "WeSpeaker ResNet152 large-margin, English VoxCeleb",
    ),
    "wespeaker_resnet221": EmbeddingModel(
        "wespeaker_resnet221",
        "wespeaker_en_voxceleb_resnet221_LM.onnx",
        256,
        "Apache-2.0",
        "WeSpeaker ResNet221 large-margin, English VoxCeleb",
    ),
    "wespeaker_resnet293": EmbeddingModel(
        "wespeaker_resnet293",
        "wespeaker_en_voxceleb_resnet293_LM.onnx",
        256,
        "Apache-2.0",
        "WeSpeaker ResNet293 large-margin, English VoxCeleb",
    ),
}


def get_embedding(model_id: str) -> EmbeddingModel:
    if model_id not in EMBEDDING:
        raise KeyError(f"Unknown embedding model '{model_id}'. Available: {', '.join(EMBEDDING)}")
    return EMBEDDING[model_id]


def existing_embeddings() -> list[EmbeddingModel]:
    return [m for m in EMBEDDING.values() if os.path.exists(m.path)]
