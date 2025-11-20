import pandas as pd
import matplotlib.pyplot as plt
from datetime import datetime
import numpy as np

# ===============================
# 1) Carregar dados
# ===============================
try:
    df = pd.read_csv("metrics_v2.csv")
except FileNotFoundError:
    print("Erro: O arquivo 'metrics.csv' não foi encontrado.")
    exit()

# Converter timestamp para datetime real
df["timestamp"] = pd.to_datetime(df["timestamp"])

# Criar coluna "node" a partir do IP+Porta
df["node"] = df["node_ip"] + ":" + df["node_port"].astype(str)

print("--- Dados carregados ---")
print(f"Total de linhas lidas: {len(df)}")
print("Nós detectados:")
for node in df["node"].unique():
    print(f"- {node}")
print("------------------------")

# ===============================
# A) Gráfico: Latência ao longo do tempo (por nó)
# ===============================
plt.figure(figsize=(12, 6))
for node in df["node"].unique():
    subset = df[df["node"] == node]
    # Certificando-se de que há dados para plotar
    if not subset.empty:
        plt.plot(subset["timestamp"], subset["latency_ms"], label=node, marker='.', linestyle='-')

plt.title("A) Latência ao longo do tempo (por nó)")
plt.xlabel("Tempo")
plt.ylabel("Latência (ms)")
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.savefig("A_latency_over_time.png", dpi=200)
plt.close()

# ===============================
# B) Latência média por nó
# ===============================
plt.figure(figsize=(8, 5))
mean_latency = df.groupby("node")["latency_ms"].mean().sort_values(ascending=False)
mean_latency.plot(kind="bar", color='skyblue')

plt.title("B) Latência média por nó")
plt.ylabel("Latência média (ms)")
plt.xticks(rotation=45, ha='right')
plt.tight_layout()
plt.grid(True, axis="y")
plt.savefig("B_latency_mean.png", dpi=200)
plt.close()

# ===============================
# C) Lamport Clock ao longo do tempo
# ===============================
plt.figure(figsize=(12, 6))
# Agrupar por nó e encontrar o Lamport mais alto para cada momento
max_lamport_per_time = df.groupby("timestamp")["lamport"].max().reset_index()

# Plotar a evolução do Lamport Clock MÁXIMO (representa o estado global)
plt.plot(max_lamport_per_time["timestamp"], max_lamport_per_time["lamport"], label="Max Lamport Clock", color='purple', linewidth=2)

plt.title("C) Lamport Clock MÁXIMO ao longo do tempo")
plt.xlabel("Tempo")
plt.ylabel("Lamport (Valor Máximo)")
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.savefig("C_lamport_time.png", dpi=200)
plt.close()

# ===============================
# D) Tempo de eleição (quando um nó falha)
# ===============================
# Lógica: Medir a duração entre a mudança de líder. Requer pelo menos 2 mudanças
# para fechar um ciclo de eleição (ex: 3 -> 1, ou 3 -> 1 -> 3)

df_sorted = df.sort_values("timestamp")

changes = []
prev_leader = None
start_time = None

for _, row in df_sorted.iterrows():
    leader = row["leader"]

    if prev_leader is None:
        prev_leader = leader
        continue

    if leader != prev_leader:
        if start_time is None:
            # Início de um novo ciclo de eleição
            start_time = row["timestamp"]
        else:
            # Fim de um ciclo de eleição (um novo líder foi estabelecido)
            end_time = row["timestamp"]
            # Calcula a duração em milissegundos
            delta = (end_time - start_time).total_seconds() * 1000
            changes.append(delta)
            
            # O início do próximo ciclo é o momento que o líder atual muda
            start_time = row["timestamp"] 

    prev_leader = leader

# Gráfico simples do tempo de eleição
plt.figure(figsize=(8, 5))

if len(changes) > 0:
    plt.plot(changes, marker='o', linestyle='-', color='red')
    plt.title("D) Duração da Eleição (ms) - Eventos Detectados")
    plt.xlabel("Evento de Eleição #")
    plt.ylabel("Duração (ms)")
    plt.grid(True)
else:
    # Se a lista 'changes' estiver vazia (seu caso), plotar uma mensagem simples
    plt.text(0.5, 0.5, "Nenhuma Duração de Eleição Completa Detectada.", 
             horizontalalignment='center', verticalalignment='center', 
             transform=plt.gca().transAxes, fontsize=12, color='darkred')
    plt.title("D) Tempo de Eleição - Sem Ciclos Completos")
    plt.xlabel("Eleição #")
    plt.ylabel("Duração (ms)")

plt.tight_layout()
plt.savefig("D_election_time.png", dpi=200)
plt.close()

print("\n✔ Gráficos gerados e salvos (PNG):")
print(" - A_latency_over_time.png")
print(" - B_latency_mean.png")
print(" - C_lamport_time.png")
print(" - D_election_time.png")
print("\nSe você quiser gerar eleições (para que o Gráfico D funcione), você deve desligar o líder (o nó com o ID mais alto) e depois ligá-lo novamente, ou chamar o endpoint /election.")