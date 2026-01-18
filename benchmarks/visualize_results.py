import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os

# Ensure output directory exists
os.makedirs('benchmarks', exist_ok=True)

# Check if CSV exists, if not create dummy data for visualization test
csv_path = 'benchmarks/benchmark_results.csv'
if not os.path.exists(csv_path):
    print(f"File {csv_path} not found. Please run AgentBenchmark.java first.")
    exit(1)

# Read the data
df = pd.read_csv(csv_path)

# Set style
sns.set_theme(style="whitegrid")
plt.figure(figsize=(15, 5))

# Chart 1: Latency Comparison
plt.subplot(1, 3, 1)
sns.barplot(data=df, x='AgentType', y='LatencyMs', hue='AgentType', palette=['#FF9999', '#99CCFF'])
plt.title('Average Latency (Lower is Better)')
plt.ylabel('Time (ms)')
plt.xlabel('Agent Version')

# Chart 2: Success Rate
plt.subplot(1, 3, 2)
success_rate = df.groupby('AgentType')['Success'].mean() * 100
success_rate.plot(kind='bar', color=['#FF9999', '#99CCFF'])
plt.title('Success Rate (Higher is Better)')
plt.ylabel('Accuracy (%)')
plt.xlabel('Agent Version')
plt.xticks(rotation=0)

# Chart 3: Token Usage
plt.subplot(1, 3, 3)
sns.barplot(data=df, x='AgentType', y='TokensUsed', hue='AgentType', palette=['#FF9999', '#99CCFF'])
plt.title('Token Efficiency (Lower is Better)')
plt.ylabel('Tokens per Task')
plt.xlabel('Agent Version')

plt.tight_layout()
plt.savefig('benchmarks/agent_performance_charts.png')
print("Charts saved to benchmarks/agent_performance_charts.png")
