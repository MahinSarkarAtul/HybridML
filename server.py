import time
from typing import List
import torch
import torch.nn as nn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

class TinyClassifier(nn.Module):
    def __init__(self):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(3, 16),
            nn.ReLU(),
            nn.Linear(16, 3),
            nn.Softmax(dim=-1)
        )

    def forward(self, x):
        return self.net(x)

model = TinyClassifier().to(device)
model.eval()

app = FastAPI(title="Hybrid ML Cloud Engine", version="1.0.0")

class PredictRequest(BaseModel):
    tensor_data: List[float]
    shape: List[int]

class PredictResponse(BaseModel):
    probabilities: List[float]
    top_class: int
    confidence: float
    server_latency_ms: float
    model_version: str

@app.post("/predict", response_model=PredictResponse)
async def predict(req: PredictRequest):
    start_time = time.perf_counter()

    if len(req.tensor_data) != 3:
        raise HTTPException(status_code=400, detail="Expected 3 float features for shape [1, 3]")

    try:
        input_tensor = torch.tensor([req.tensor_data], dtype=torch.float32, device=device)

        with torch.no_grad():
            output = model(input_tensor)
            probs = output.squeeze(0).cpu().tolist()

        top_confidence = max(probs)
        top_class = probs.index(top_confidence)
        duration_ms = (time.perf_counter() - start_time) * 1000.0

        return PredictResponse(
            probabilities=probs,
            top_class=top_class,
            confidence=top_confidence,
            server_latency_ms=round(duration_ms, 2),
            model_version="v1.0-cuda"
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    print(f"Starting server on device: {device}")
    uvicorn.run(app, host="0.0.0.0", port=8000)
