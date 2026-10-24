(()=>{
  'use strict';
  const $=id=>document.getElementById(id);
  const card=$('card'),bar=$('bar'),percentEl=$('percent'),bytesEl=$('bytes'),speedEl=$('speed'),etaEl=$('eta');
  const title=$('title'),message=$('message'),stage=$('stage'),status=$('status'),detail=$('detail'),retry=$('retry');
  const track=document.querySelector('.track');
  const steps=Array.prototype.slice.call(document.querySelectorAll('#steps li'));

  let sawDownload=false,smoothSpeed=0,lastDone=-1,lastTime=0;

  // ---------- Lecteur de musique (pistes dans music/) ----------
  const TRACKS=[
    {src:'music/track1.mp3',title:'SPRINTER - CENTRAL CEE x DAVE'},
    {src:'music/track2.mp3',title:'PILIER - NINHO'},
    {src:'music/track3.mp3',title:'PACMAN DE PANICAT - MC RYAN SP, MC POZE DO RODO, RODRIGO DO CN'},
    {src:'music/track4.mp3',title:'PACMAN DE PANICAT, EU PREFIRO A RUA'},
    {src:'music/track5.mp3',title:'PACMAN DE PANICAT - BASE AUTOMOTIVA'},
    {src:'music/track6.mp3',title:'MERIDIAN - TIAKOLA x DAVE'}
  ];
  const audio=new Audio();
  audio.preload='auto';
  const titleEl=$('trackTitle'),playBtn=$('play'),playIcon=$('playIcon'),prevBtn=$('prev'),nextBtn=$('next'),vol=$('vol');
  const ICON_PAUSE='M7 5h4v14H7zM13 5h4v14h-4z',ICON_PLAY='M8 5v14l11-7z';
  let trackIndex=Math.floor(Math.random()*TRACKS.length),errors=0,stopped=false;

  function showIcon(playing){
    playIcon.firstElementChild.setAttribute('d',playing?ICON_PAUSE:ICON_PLAY);
    playBtn.setAttribute('aria-label',playing?'Pause':'Lecture');
  }
  function loadTrack(i){
    trackIndex=(i+TRACKS.length)%TRACKS.length;
    audio.src=TRACKS[trackIndex].src;
    titleEl.textContent=TRACKS[trackIndex].title;
  }
  function playAudio(){
    if(stopped)return;
    const p=audio.play();
    if(p&&typeof p.catch==='function')p.catch(()=>showIcon(false));
  }
  function setVolume(v){
    v=Math.max(0,Math.min(100,Number(v)||0));
    audio.volume=v/100;
    vol.style.setProperty('--v',v+'%');
  }
  function stopMusic(){
    stopped=true;
    try{audio.pause();audio.removeAttribute('src');audio.load();}catch(e){}
  }
  audio.addEventListener('play',()=>showIcon(true));
  audio.addEventListener('pause',()=>showIcon(false));
  audio.addEventListener('playing',()=>{errors=0;});
  audio.addEventListener('ended',()=>{loadTrack(trackIndex+1);playAudio();});
  audio.addEventListener('error',()=>{
    if(stopped||++errors>TRACKS.length)return;
    window.setTimeout(()=>{loadTrack(trackIndex+1);playAudio();},300);
  });
  playBtn.addEventListener('click',()=>{if(audio.paused)playAudio();else audio.pause();});
  nextBtn.addEventListener('click',()=>{loadTrack(trackIndex+1);playAudio();});
  prevBtn.addEventListener('click',()=>{
    if(audio.currentTime>3){audio.currentTime=0;return;}
    loadTrack(trackIndex-1);playAudio();
  });
  vol.addEventListener('input',()=>setVolume(vol.value));
  setVolume(vol.value);
  loadTrack(trackIndex);
  playAudio();

  const clamp=v=>Math.max(0,Math.min(100,Number(v)||0));
  const norm=t=>String(t||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
  const dec=(v,d)=>v.toFixed(d).replace('.',',');
  const fmtBytes=v=>{
    v=Math.max(0,Number(v)||0);
    if(v<1048576)return Math.round(v/1024)+' Ko';
    if(v<1073741824)return dec(v/1048576,1)+' Mo';
    return dec(v/1073741824,2)+' Go';
  };
  const fmtTime=s=>{
    s=Math.max(0,Math.round(s));
    if(s<60)return s+' s';
    const m=Math.floor(s/60);
    if(m<60)return m+' min '+String(s%60).padStart(2,'0')+' s';
    return Math.floor(m/60)+' h '+String(m%60).padStart(2,'0')+' min';
  };

  // Textes affichés pour chaque étape (la phrase exacte envoyée par l'application s'affiche dans #message).
  const INFO={
    prepare:{state:'idle',stage:'PRÉPARATION',title:'Installation des données du jeu',status:'Vérification en cours',
      detail:'Recherche d’une installation existante avant tout téléchargement.'},
    download:{state:'download',stage:'TÉLÉCHARGEMENT',title:'Installation des données du jeu',status:'Téléchargement des fichiers du jeu',
      detail:'Cette étape n’a lieu qu’une seule fois. Gardez l’application ouverte.'},
    verify:{state:'verify',stage:'VÉRIFICATION',title:'Installation des données du jeu',status:'Vérification de l’intégrité',
      detail:'Contrôle du fichier téléchargé avant son installation.'},
    install:{state:'install',stage:'INSTALLATION',title:'Installation des données du jeu',status:'Installation des fichiers du jeu',
      detail:'Extraction en cours : ne fermez pas l’application.'}
  };

  function setSteps(current,allDone){
    steps.forEach((li,i)=>{
      const n=i+1;
      li.className=allDone||n<current?'done':n===current?'active':'';
    });
  }
  function setBar(pct){
    bar.style.width=pct+'%';
    percentEl.textContent=Math.round(pct)+' %';
    track.setAttribute('aria-valuenow',String(Math.round(pct)));
  }

  function setProgress(value,phase,done,total){
    const pct=clamp(value),p=norm(phase),now=Date.now();
    done=Math.max(0,Number(done)||0);
    total=Math.max(0,Number(total)||0);

    let key='prepare',step=1;
    if(p==='donnees installees'||pct>=100){key='install';step=4;}
    else if(/installation des fichiers|extraits|finalisation/.test(p)){key='install';step=3;}
    else if(/integrite/.test(p)){key='verify';step=2;}
    else if(/^telechargement|reprise/.test(p)){key='download';step=2;}
    const info=INFO[key];

    // Vitesse lissée (calculée ici, indépendamment de l'application)
    if(key==='download'){
      sawDownload=true;
      if(lastDone>=0&&done<lastDone)smoothSpeed=0;
      if(lastDone>=0&&done>lastDone&&now>lastTime){
        const instant=(done-lastDone)*1000/(now-lastTime);
        smoothSpeed=smoothSpeed?smoothSpeed*0.8+instant*0.2:instant;
      }
      lastDone=done;lastTime=now;
    }else{
      smoothSpeed=0;lastDone=-1;
    }

    card.setAttribute('data-state',info.state);
    stage.textContent=info.stage;
    title.textContent=info.title;
    message.textContent=phase||info.title;
    if(/nouvelle tentative/.test(p)){
      status.textContent='Connexion instable';
      detail.textContent='Nouvelle tentative automatique en cours…';
    }else{
      status.textContent=info.status;
      detail.textContent=info.detail;
    }
    setSteps(step,pct>=100);
    setBar(pct);
    bytesEl.textContent=total>0?fmtBytes(done)+' sur '+fmtBytes(total):'En attente des données…';
    speedEl.textContent=key==='download'&&smoothSpeed>0?'Vitesse : '+fmtBytes(smoothSpeed)+'/s':'';
    etaEl.textContent=key==='download'&&smoothSpeed>0&&total>done?'Temps restant : environ '+fmtTime((total-done)/smoothSpeed):'';
    retry.hidden=true;
  }

  function connecting(){
    card.setAttribute('data-state','idle');
    setSteps(4,true);
    stage.textContent='CONNEXION';
    title.textContent='Connexion au serveur';
    message.textContent='Chargement de Crestwood Roleplay en cours…';
    status.textContent='Authentification en cours';
    detail.textContent='Cet écran se fermera dès que le bouton Spawn apparaîtra.';
    bytesEl.textContent='Chargement du monde…';
    percentEl.textContent='…';
    track.classList.add('busy');
    speedEl.textContent='';
    etaEl.textContent='';
    retry.hidden=true;
  }

  function ready(){
    card.setAttribute('data-state','ready');
    setSteps(4,true);
    setBar(100);
    stage.textContent='TERMINÉ';
    title.textContent='Données prêtes';
    message.textContent=sawDownload?'L’installation est terminée. Bon jeu !':'Vos données sont déjà installées : aucun téléchargement n’a été nécessaire.';
    status.textContent='Installation validée';
    detail.textContent=sawDownload?'Téléchargement, vérification et installation terminés.':'Les fichiers du jeu ont été trouvés sur cet appareil.';
    speedEl.textContent='';
    etaEl.textContent='';
    retry.hidden=true;
  }

  function error(text){
    card.setAttribute('data-state','error');
    stage.textContent='ACTION REQUISE';
    title.textContent='Installation en pause';
    message.textContent=text||'Impossible de terminer l’installation des données.';
    status.textContent='L’installation doit être reprise';
    detail.textContent='Le jeu reste bloqué tant que les fichiers obligatoires ne sont pas installés.';
    speedEl.textContent='';
    etaEl.textContent='Appuyez sur RÉESSAYER pour reprendre.';
    retry.hidden=false;
  }

  function storageAccessRequired(){
    card.setAttribute('data-state','permission');
    setSteps(1,false);
    stage.textContent='AUTORISATION REQUISE';
    title.textContent='Autorisez le stockage';
    message.textContent='Autorisez l’accès aux fichiers pour installer les données dans /storage/emulated/0/data.';
    status.textContent='En attente de l’autorisation';
    detail.textContent='Dès que l’accès est accordé, l’installation démarre automatiquement.';
    speedEl.textContent='';
    etaEl.textContent='';
    retry.hidden=true;
  }

  function storageAccessGranted(){
    card.setAttribute('data-state','idle');
    title.textContent='Préparation des données du jeu';
    message.textContent='Accès autorisé. Démarrage de l’installation…';
    status.textContent='Préparation du téléchargement';
    detail.textContent='Les fichiers seront installés dans /storage/emulated/0/data.';
  }

  retry.addEventListener('click',()=>{
    retry.hidden=true;
    card.setAttribute('data-state','idle');
    setSteps(1,false);
    stage.textContent='REPRISE';
    title.textContent='Reprise de l’installation';
    message.textContent='Vérification des fichiers déjà téléchargés…';
    status.textContent='Préparation de la reprise';
    detail.textContent='Un fichier valide déjà téléchargé sera réutilisé.';
    etaEl.textContent='';
    if(window.AndroidLoading&&typeof window.AndroidLoading.retry==='function')window.AndroidLoading.retry();
  });

  window.LoadingBridge={setProgress,ready,error,storageAccessRequired,storageAccessGranted,stopMusic,connecting};
  setSteps(1,false);
  if(window.AndroidLoading&&typeof window.AndroidLoading.start==='function')window.AndroidLoading.start();
})();
