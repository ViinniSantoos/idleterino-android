# Idleterino para Android

O Idleterino no celular: as suas contas do PokeIdle farmando juntas, as ferramentas do Idleterino do PC e os pontos da Twitch e da Kick.

## O que tem

- **Principal + ALTs:** até 4 contas ao mesmo tempo. Cada uma tem o login separado. As ALTs ficam no modo ULTRA, que é leve.
- **Painel "I" dentro do jogo:**
  - Início: placar da sessão, previsão de shiny e bônus de XP;
  - Melhor hunt e Próximas;
  - Evoluir e Comprar (Market);
  - PvP: meta do top 50, tier list, seu time contra o meta e compras;
  - Contas, Ajustes e Apoiar.
- **Twitch e Kick:**
  - assiste até 2 lives oficiais do PokeIdle escondidas, sem som e em 160p;
  - mostra os pontos de cada canal;
  - pega o baú de bônus da Twitch sozinho;
  - nunca compra nada na loja sozinho.
- **Trazer as contas do PC:** no Idleterino do PC, abra Ajustes › "Levar contas para o celular" e leia o QR. As contas chegam criptografadas e precisam do PIN de 8 números.
- **Atualização:** a versão nova baixa sozinha e aparece "Atualizar agora".

## Instalar

1. Baixe o `Idleterino-android.apk` da [última versão](https://github.com/ViinniSantoos/idleterino-android/releases/latest).
2. Abra o arquivo no celular e permita "instalar apps desta fonte".
3. Pronto. As próximas versões o próprio app oferece.

## Segurança

O celular tem banco, WhatsApp e afins. Por isso o app foi feito para não chegar perto de nada disso:

- **Permissões:** só pede internet, notificações, manter o jogo rodando e instalar a própria atualização. Não pede contatos, SMS, arquivos, câmera, microfone nem localização.
- **Logins:** ficam criptografados com uma chave do Android Keystore. O código de acesso das contas nunca volta para a página.
- **Ponte com as páginas:** cada ponte entre página e app só aceita o próprio site (https://pokeidle.io, twitch.tv, kick.com).
- **Navegador interno:** não abre arquivos, não baixa nada e usa o Safe Browsing. Tudo que não for o jogo, a Twitch ou a Kick abre fora do app.
- **Backup:** o Android não faz backup dos dados do app.
- **Atualização:** antes de instalar, o app confere se o arquivo tem a mesma assinatura do Idleterino instalado.

## Gerar o APK

Kotlin + Android SDK 35 (Gradle 8.11):

```bash
node scripts/gerar-assets.js   # núcleo do Idleterino do PC + painel
gradle assembleRelease
```

A chave de assinatura (`keystore.properties` + `*.jks`) não fica no repositório.

Feito por um jogador, de graça para todo mundo. ❤ Se ajudou, dá para apoiar pelo Pix no próprio app.
