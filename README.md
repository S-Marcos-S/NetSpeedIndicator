# Net Speed

Net Speed é um aplicativo para Android desenvolvido em Kotlin que fornece monitoramento em tempo real da velocidade de internet e do consumo de dados diretamente na barra de notificações do dispositivo.

## Lógica de Funcionamento

O aplicativo opera através de um serviço em primeiro plano (Foreground Service) que utiliza a API TrafficStats do Android. Esta API fornece contadores acumulativos de bytes transmitidos e recebidos desde o boot do sistema.

### Monitoramento de Velocidade
A velocidade é calculada através da diferença de bytes trafegados em um intervalo de um segundo. O aplicativo diferencia pacotes de download (recebidos) e upload (enviados) para fornecer leituras precisas de ambas as direções.

### Indicador Dinâmico
O ícone exibido na barra de status é gerado dinamicamente via código. Ele desenha um bitmap contendo o valor numérico da velocidade predominante e uma seta indicativa (cima para upload, baixo para download), permitindo a visualização da velocidade mesmo com o painel de notificações fechado.

### Gestão de Consumo Diário
O aplicativo registra o consumo total de Dados Móveis e Wi-Fi separadamente. Ele armazena uma base de referência no início do dia e calcula o consumo atual subtraindo essa base dos valores totais do sistema. O contador é resetado automaticamente à meia-noite.

### Otimização de Bateria
Para garantir o gasto mínimo de bateria, o aplicativo implementa as seguintes estratégias:
- Suspensão de Processamento: O cálculo de velocidade e a atualização da interface são interrompidos completamente quando a tela do dispositivo está desligada.
- Detecção de Mudança: A notificação só é atualizada pelo sistema quando há uma mudança significativa nos valores de velocidade ou consumo, evitando wakeups desnecessários da CPU.
- Limite de Exibição (Threshold): O usuário pode definir um valor mínimo de velocidade. Se o tráfego for inferior a este limite, o indicador dinâmico é ocultado para economizar processamento gráfico.

## Privacidade e Segurança

A privacidade do usuário é um pilar fundamental do Net Speed. O aplicativo funciona sob as seguintes premissas de segurança:

1. Sem Coleta de Dados: O aplicativo não coleta, não armazena e não transmite nenhum dado pessoal ou de navegação do usuário.
2. Acesso Local: Todos os cálculos de rede são feitos localmente no dispositivo usando APIs padrão do Android que não requerem acesso ao conteúdo do tráfego.
3. Sem Conexões Externas: O aplicativo não possui servidores próprios e não realiza conexões de rede externas para envio de estatísticas ou telemetria.
4. Permissões Mínimas: O aplicativo solicita apenas as permissões estritamente necessárias para o monitoramento de rede e exibição de notificações.

## Requisitos de Sistema

- Android 7.0 (API 24) ou superior.
- Permissão de Notificações (Android 13+).
- Permissão de Serviço em Primeiro Plano.
