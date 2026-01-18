# TapMate Hackathon Repository

## 1. Project Setup
This is a standard Android Java project. Open `TapMate` in Android Studio.

## 2. Training the Agent
We use **Supervised Fine-Tuning** on Gemini 3 Flash.
1. Go to [Google AI Studio](https://aistudio.google.com/).
2. Create a new "Tuned Model".
3. Upload `training/tapmate_gui_training.jsonl`.
4. Run the tuning job.
5. Get your Model ID and put it in `TapMate/app/src/main/java/com/nexhacks/tapmate/gemini/GeminiClient.java`.

## 3. Benchmarking
To prove our agent is better than the baseline:
1. Run the benchmark simulation:
   ```bash
   javac benchmarks/AgentBenchmark.java
   java -cp benchmarks com.nexhacks.tapmate.benchmarks.AgentBenchmark
   ```
2. Generate the charts:
   ```bash
   python3 benchmarks/visualize_results.py
   ```
3. Use `benchmarks/agent_performance_charts.png` in your presentation.

## 4. Architecture
*   **Brain:** Gemini 3 Flash (Vertex AI)
*   **Eye:** Overshoot Live Vision
*   **Hand:** Android Accessibility Service
*   **Memory:** Room Database

## 5. Key Files
*   `TapMateAccessibilityService.java`: Extracts UI state as JSON.
*   `MemoryItem.java`: Stores "Recalled" info (License Plates, etc).
*   `tapmate_gui_training.jsonl`: The dataset that makes our agent smart.
