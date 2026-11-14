/* ============================================================
   CONFIGURACION: imagenes, videos y contacto
   Aqui pones tus archivos. Cada "espacio" de la pagina tiene un
   nombre. Si src esta vacio, se muestra un recuadro para recordarte.
   tipo:  'img'      -> imagen guardada en web/img/      (src:'img/foto.jpg')
          'video'    -> video guardado en web/video/     (src:'video/clip.mp4')
          'youtube'  -> video de YouTube                 (src:'ID_DEL_VIDEO')
   ============================================================ */
const MEDIOS = {
  // Inicio
  'hero':            { tipo: 'img', src: 'img/hero.jpg' },
  'card-requisitos': { tipo: 'img', src: 'img/requisitos.jpg' },
  'card-proceso':    { tipo: 'img', src: 'img/proceso.jpg' },
  'card-mitos':      { tipo: 'img', src: 'img/mitos.jpg' },
  'card-cuidados':   { tipo: 'img', src: 'img/cuidados.jpg' },
  'amor':            { tipo: 'img', src: 'img/amor.jpg' },   // fondo oscuro "Donar sangre es un acto de amor"
  // Requisitos
  'requisitos':      { tipo: 'youtube', src: 'Tk_94s-_8PM' },
  // Proceso (un espacio por paso)
  'paso-1':          { tipo: 'img', src: 'img/paso1.jpg' },
  'paso-2':          { tipo: 'img', src: 'img/paso2.jpg' },
  'paso-3':          { tipo: 'img', src: 'img/paso3.jpg' },
  'paso-4':          { tipo: 'img', src: 'img/paso4.jpg' },
  // Mitos
  'mitos':           { tipo: 'youtube', src: 'U7EwrJ5pXAI' },
  // Cuidados
  'cuidados-antes':  { tipo: 'img', src: 'img/cuidados1.jpg' },
  'cuidados-despues':{ tipo: 'img', src: 'img/cuidados2.jpg' },
};

/* Contacto y redes (deja '' para ocultar) */
const CONTACTO = { telefono: '0000000', correo: 'wfel0000', direccion: '00000' };
const REDES = { instagram: 'enBjMGVtcWJ4Z3pu', facebook: 'enBjMGVtcWJ4Z3pu', x: 'enBjMGVtcWJ4Z3pu', youtube: 'enBjMGVtcWJ4Z3pu' };   // pega aqui el enlace completo
