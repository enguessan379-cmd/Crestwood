# Investigação — atualização e fundos

- O arquivo `app/src/main/res/drawable/download_background.webp` é um fundo panorâmico existente para a tela de atualização, com silhueta urbana escura e aeronave.
- O envio `upload/file_000000007c2c8243bb78c93e0c135dd4.png` é a referência visual da Home Crestwood, incluindo a composição dourada, cidade, viatura e logotipo.
- A Home atual usa apenas `launcher_background.xml`, um gradiente sem imagem, e por isso perdeu a imagem de fundo solicitada.
- A tela HTML de atualização atualmente usa gradiente CSS e não referencia `download_background.webp`, deixando o fundo visual ausente.
