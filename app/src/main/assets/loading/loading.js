(()=>{
  'use strict';
  const $=id=>document.getElementById(id);
  const bar=$('bar'),percent=$('percent'),bytes=$('bytes'),transfer=$('transfer');
  const title=$('title'),message=$('message'),stage=$('stage'),status=$('status');
  const detail=$('detail'),retry=$('retry'),dot=$('statusDot'),track=document.querySelector('.track');
  let current={percent:0,done:0,total:0,speed:0,phase:''};
  const clamp=value=>Math.max(0,Math.min(100,Number(value)||0));
  const formatBytes=value=>{const bytesValue=Math.max(0,Number(value)||0);if(bytesValue<1024*1024)return `${Math.round(bytesValue/1024)} KB`;if(bytesValue<1024*1024*1024)return `${(bytesValue/(1024*1024)).toFixed(1)} MB`;return `${(bytesValue/(1024*1024*1024)).toFixed(2)} GB`};
  const formatTime=value=>{const seconds=Math.max(0,Math.round(Number(value)||0));if(seconds<60)return `${seconds}s restantes`;const minutes=Math.floor(seconds/60);return minutes<60?`${minutes}min ${seconds%60}s restantes`:`${Math.floor(minutes/60)}h ${minutes%60}min restantes`};
  const isDownload=phase=>/baixando|download concluído|retomando/i.test(phase||'');
  const isVerify=phase=>/verificando integridade|verificando arquivo/i.test(phase||'');
  const isExtract=phase=>/instalando|extraíd|extraindo|finalizando/i.test(phase||'');
  function setHealthy(){if(dot){dot.style.background='#50e2a0';dot.style.boxShadow='0 0 12px rgba(80,226,160,.75)'}if(track)track.setAttribute('aria-valuenow',String(Math.round(current.percent)))}
  function setProgress(value,phase,done,total,speed){
    current={percent:clamp(value),phase:String(phase||''),done:Math.max(0,Number(done)||0),total:Math.max(0,Number(total)||0),speed:Math.max(0,Number(speed)||0)};
    if(bar)bar.style.width=`${current.percent}%`;
    if(percent)percent.textContent=`${Math.round(current.percent)}%`;
    if(message&&current.phase)message.textContent=current.phase;
    if(retry)retry.hidden=true;
    setHealthy();
    const downloading=isDownload(current.phase),verifying=isVerify(current.phase),extracting=isExtract(current.phase);
    if(stage)stage.textContent=downloading?'DOWNLOAD DA DATA':verifying?'VERIFICAÇÃO':extracting?'INSTALAÇÃO DA DATA':current.percent>=100?'CONCLUÍDO':'PREPARANDO';
    if(bytes){
      if(current.total>0)bytes.textContent=`${formatBytes(current.done)} de ${formatBytes(current.total)}`;
      else bytes.textContent='Aguardando dados do servidor';
    }
    if(downloading){
      if(status)status.textContent='Baixando arquivos do jogo';
      if(detail)detail.textContent='Transferência direta para o armazenamento privado do aplicativo.';
      if(transfer)transfer.textContent=current.speed>0?`${formatBytes(current.speed)}/s · ${formatTime((current.total-current.done)/current.speed)}`:'Calculando velocidade de transferência...';
    }else if(verifying){
      if(status)status.textContent='Verificando integridade do arquivo';
      if(detail)detail.textContent='Conferindo se o arquivo baixado não está corrompido antes de instalar.';
      if(transfer)transfer.textContent='Isso pode levar alguns segundos.';
    }else if(extracting){
      if(status)status.textContent='Instalando arquivos do jogo';
      if(detail)detail.textContent='A data está sendo extraída em uma área protegida antes de liberar o jogo.';
      if(transfer)transfer.textContent='Não feche o aplicativo durante a instalação.';
    }else if(current.percent>=100){
      if(status)status.textContent='Data instalada com sucesso';
      if(detail)detail.textContent='A verificação foi concluída. Preparando a tela inicial.';
      if(transfer)transfer.textContent='Download, validação e instalação concluídos.';
    }else{
      if(status)status.textContent='Conectando ao servidor';
      if(detail)detail.textContent='Verificando a versão e preparando a instalação segura.';
      if(transfer)transfer.textContent='Conexão segura via HTTPS';
    }
  }
  function ready(){
    if(title)title.textContent='Data pronta para jogar';
    setProgress(100,'Data instalada',current.total||current.done,current.total||current.done,0);
  }
  function error(text){
    if(title)title.textContent='Instalação pausada';
    if(message)message.textContent=text||'Não foi possível concluir a instalação da data.';
    if(stage)stage.textContent='AÇÃO NECESSÁRIA';
    if(status)status.textContent='A instalação precisa ser retomada';
    if(detail)detail.textContent='O jogo continuará bloqueado até os arquivos obrigatórios serem instalados corretamente.';
    if(transfer)transfer.textContent='Toque em TENTAR NOVAMENTE para continuar de forma segura.';
    if(retry)retry.hidden=false;
    if(dot){dot.style.background='#ff5d73';dot.style.boxShadow='0 0 12px rgba(255,93,115,.75)'}
  }
  function storageAccessRequired(){
    if(title)title.textContent='Autorize o armazenamento';
    if(message)message.textContent='Permita o acesso aos arquivos para instalar a data em /storage/emulated/0/GTA.';
    if(stage)stage.textContent='AUTORIZAÇÃO NECESSÁRIA';
    if(status)status.textContent='Aguardando permissão de armazenamento';
    if(detail)detail.textContent='Após autorizar, a instalação será iniciada automaticamente.';
    if(transfer)transfer.textContent='A pasta pública GTA será criada na memória interna.';
    if(retry)retry.hidden=true;
  }
  function storageAccessGranted(){
    if(title)title.textContent='Preparando a data do jogo';
    if(message)message.textContent='Acesso autorizado. Iniciando a instalação...';
    if(status)status.textContent='Preparando o download';
    if(detail)detail.textContent='Os arquivos serão instalados em /storage/emulated/0/GTA.';
  }
  if(retry)retry.addEventListener('click',()=>{
    retry.hidden=true;
    if(title)title.textContent='Retomando a instalação';
    if(message)message.textContent='Verificando os arquivos já baixados...';
    if(status)status.textContent='Preparando a retomada';
    if(detail)detail.textContent='O arquivo válido já baixado será reutilizado.';
    if(transfer)transfer.textContent='Mantendo o progresso concluído.';
    if(window.AndroidLoading&&typeof window.AndroidLoading.retry==='function')window.AndroidLoading.retry();
  });
  window.LoadingBridge={setProgress,ready,error,storageAccessRequired,storageAccessGranted};
  if(window.AndroidLoading&&typeof window.AndroidLoading.start==='function')window.AndroidLoading.start();
})();
