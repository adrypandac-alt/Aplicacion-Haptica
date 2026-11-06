from pathlib import Path
import json
from xml.sax.saxutils import escape
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, Image, XPreformatted, Flowable

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/pdf'
OUT.mkdir(parents=True, exist_ok=True)
PDF = OUT / 'Informe_detallado_unificacion_V3Integracion1.0.pdf'
FONT = Path('C:/Windows/Fonts')
for name, file in [('Arial','arial.ttf'),('Arial-Bold','arialbd.ttf'),('Arial-Italic','ariali.ttf'),('Mono','consola.ttf')]:
    pdfmetrics.registerFont(TTFont(name, str(FONT/file)))
pdfmetrics.registerFontFamily('Arial', normal='Arial', bold='Arial-Bold', italic='Arial-Italic', boldItalic='Arial-Bold')
INK = colors.HexColor('#18283A')
TEAL = colors.HexColor('#096B60')
MIST = colors.HexColor('#EEF5F2')
GRAY = colors.HexColor('#536273')
LINE = colors.HexColor('#D7E2DF')
WIDTH = A4[0]-92
styles = {
    'body': ParagraphStyle('body',fontName='Arial',fontSize=10.2,leading=14.5,textColor=INK,spaceAfter=9),
    'small': ParagraphStyle('small',fontName='Arial',fontSize=8.6,leading=11.7,textColor=GRAY,spaceAfter=7,splitLongWords=True),
    'cell': ParagraphStyle('cell',fontName='Arial',fontSize=9.0,leading=12,textColor=INK,splitLongWords=True),
    'headcell': ParagraphStyle('headcell',fontName='Arial-Bold',fontSize=9,leading=12,textColor=colors.white),
    'h1': ParagraphStyle('h1',fontName='Arial-Bold',fontSize=23,leading=27,textColor=INK,spaceAfter=16),
    'h2': ParagraphStyle('h2',fontName='Arial-Bold',fontSize=12.5,leading=16,textColor=TEAL,spaceBefore=8,spaceAfter=7),
    'kicker': ParagraphStyle('kicker',fontName='Arial-Bold',fontSize=8.8,leading=12,textColor=TEAL,spaceAfter=9),
    'cover': ParagraphStyle('cover',fontName='Arial-Bold',fontSize=34,leading=39,textColor=INK,spaceAfter=20),
    'code': ParagraphStyle('code',fontName='Mono',fontSize=8,leading=11,textColor=INK,backColor=MIST,borderPadding=10,spaceAfter=12),
}
story=[]
def p(text, style='body'):
    return Paragraph(text, styles[style])
def add(text, style='body'):
    story.append(p(text,style))
def title(n,text):
    if story: story.append(PageBreak())
    add(f'INFORME TÉCNICO  /  {n:02d}', 'kicker')
    add(text,'h1')
def h(text): add(text,'h2')
def table(headers, rows, widths=None):
    data=[[p(escape(str(x)),'headcell') for x in headers]]
    data.extend([[p(escape(str(x)),'cell') for x in row] for row in rows])
    t=Table(data,colWidths=widths or [WIDTH/len(headers)]*len(headers),repeatRows=1,hAlign='LEFT')
    t.setStyle(TableStyle([
        ('BACKGROUND',(0,0),(-1,0),TEAL),('VALIGN',(0,0),(-1,-1),'TOP'),
        ('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),
        ('TOPPADDING',(0,0),(-1,-1),7),('BOTTOMPADDING',(0,0),(-1,-1),7),
        ('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,MIST]),
        ('LINEBELOW',(0,0),(-1,0),0.6,TEAL),('LINEBELOW',(0,1),(-1,-1),0.3,LINE),
    ]))
    story.extend([t,Spacer(1,12)])
def note(text):
    t=Table([[p(text)]],colWidths=[WIDTH])
    t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,-1),MIST),('BOX',(0,0),(-1,-1),0.5,LINE),('LEFTPADDING',(0,0),(-1,-1),12),('RIGHTPADDING',(0,0),(-1,-1),12),('TOPPADDING',(0,0),(-1,-1),10),('BOTTOMPADDING',(0,0),(-1,-1),3)]))
    story.extend([t,Spacer(1,12)])
def code(text): story.append(XPreformatted(escape(text),styles['code']))
class Pipeline(Flowable):
    def __init__(self, stages, foot=''):
        Flowable.__init__(self); self.stages=stages; self.width=WIDTH; self.height=len(stages)*44+12+(20 if foot else 0); self.foot=foot
    def draw(self):
        c=self.canv
        for i,s in enumerate(self.stages):
            y=self.height-38-i*44
            c.setFillColor(MIST);c.setStrokeColor(LINE);c.roundRect(0,y,self.width,31,6,fill=1,stroke=1)
            c.setFillColor(INK);c.setFont('Arial-Bold',9.5);c.drawCentredString(self.width/2,y+11,s)
            if i<len(self.stages)-1:
                c.setStrokeColor(TEAL); c.line(self.width/2,y-2,self.width/2,y-10)
                c.line(self.width/2,y-10,self.width/2-3,y-7);c.line(self.width/2,y-10,self.width/2+3,y-7)
        if self.foot:
            c.setFont('Arial-Italic',8);c.setFillColor(GRAY);c.drawString(0,0,self.foot)
def screenshots(names,captions):
    cells=[]
    for name,caption in zip(names,captions):
        img=Image(str(ROOT.parent/'Capturas'/'V3'/name),width=195,height=195*2400/1080)
        cells.append([img,Spacer(1,8),p(caption,'small')])
    t=Table([cells],colWidths=[WIDTH/2]*2)
    t.setStyle(TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),6),('RIGHTPADDING',(0,0),(-1,-1),10)]))
    story.append(t)

# 1
add('DOCUMENTACIÓN DE LA ENTREGA  /  29 OCTUBRE 2026','kicker')
story.append(Spacer(1,26))
add('Cómo se unificó<br/>V3Integracion1.0','cover')
add('Arquitectura, adaptación de interfaz, conservación de algoritmos y evidencia de validación.','h2')
add('Este informe explica la integración realizada sobre una copia de PrototipoV3. Describe qué componentes se reutilizaron, cómo se conectaron, qué archivos cambiaron y qué capacidades siguen pendientes.')
story.append(Spacer(1,12))
table(['Componente','Resultado de esta entrega'],[
    ['Cotidiano','Motor YAMNet + clasificador propio integrado; cuatro contextos en la interfaz.'],
    ['Música','Biblioteca AudioToHaptics incorporada y conectada a su API por archivo. Demucs sigue ausente.'],
    ['Háptica','Se reutiliza BLE para los patrones cotidianos existentes. No se añadió firmware musical multicanal.'],
    ['Verificación','43 pruebas superadas y 6 omitidas por recursos externos ausentes.'],
],[105,WIDTH-105])
note('<b>Criterio de trabajo:</b> unificar lo entregado sin completar los modelos, sin entrenar nuevas clases y sin modificar los algoritmos de clasificación o síntesis. Las dos excepciones de código se documentan en la sección de cambios: cierre de recursos y disponibilidad de datos de una prueba.')
add('Proyecto: V3/V3Integracion1.0<br/>Versión de la app: 3.0-integracion1.0<br/>Documento basado en código, capturas e informes locales de esta entrega.','small')

# 2
title(1,'Guía de lectura y alcance')
table(['Página','Contenido'],[
    ['3','Arquitectura y estructura del proyecto'],['4','Compilación, dependencias y recursos'],
    ['5','Funcionamiento del motor cotidiano'],['6','Contextos y capacidades reales'],
    ['7','Sesiones, micrófono y ciclo de vida'],['8','Alertas, Bluetooth y firmware'],
    ['9','Funcionamiento de la biblioteca musical'],['10','Conexión musical con la aplicación'],
    ['11','Interfaz cotidiana: capturas y decisiones'],['12','Interfaz de Inicio y Música'],
    ['13','Cambios directos en las fuentes de los equipos'],['14','Pruebas y auditoría'],
    ['15','Cómo compilar, utilizar y continuar la entrega'],['16','Mapa de archivos y límites finales'],
],[55,WIDTH-55])
h('Qué significa aquí «integrar»')
add('Se creó una aplicación Android que contiene ambas bibliotecas y utiliza sus API reales. La interfaz presenta sus estados y sus capacidades, en lugar de anunciar funciones que los archivos recibidos no pueden ejecutar. Cotidiano dispone de sus modelos; Música está integrada a nivel de código, compilación e interfaz, pero su análisis completo sigue bloqueado por la ausencia de Demucs.')
add('Las carpetas fuente Audio y Cotidiano permanecieron sin editar durante esta integración. PrototipoV3 también se conservó; las adaptaciones de esta etapa están dentro de V3Integracion1.0.')
add('Las referencias del informe son rutas relativas a la raíz de V3Integracion1.0, salvo cuando se indica expresamente un origen externo. Las pruebas del emulador no equivalen a una validación del brazalete físico.','small')

# 3
title(2,'Arquitectura unificada')
add('La aplicación se organizó en tres responsabilidades: presentación, coordinación y motores. Compose muestra controles y resultados; IntegracionViewModel administra las sesiones y las salidas; los módulos de los equipos ejecutan el reconocimiento o el procesamiento musical.')
story.append(Pipeline(['Interfaz Compose: Inicio / Música / Cotidiano','IntegracionViewModel: estado, permisos, sesiones y resultados','RegistroAlgoritmos: construye las bibliotecas reales','sound-engine (Cotidiano)  |  audioToHaptics (Música)']))
h('Estructura principal')
code('V3Integracion1.0/\n  app/                      Interfaz y coordinación\n  sound-engine/             Motor cotidiano recibido\n  audioToHaptics/            Biblioteca musical recibida\n  audioToHapticsModels/      Empaquetado opcional de Demucs\n  models/                   Ubicación del modelo pendiente\n  firmware/                 Firmware conservado de la app\n  docs/                     Cambios, pruebas y capturas\n  scripts/                  Auditoría e informe PDF')
add('<b>Origen cotidiano:</b> Cotidiano/sound-recognition-main/sound-recognition-main/Android/sound-engine.<br/><b>Origen musical:</b> Audio/Tesis-main/Tesis-main/AudioToHaptics/audioToHaptics y audioToHapticsModels.','small')
add('Se eligieron los componentes Android ya existentes. Los scripts Python de entrenamiento, exportación y experimentación no se ejecutan dentro de la app. El motor cotidiano mantiene su propia captura; la biblioteca musical conserva su decodificación por URI. No se forzó a ambos a pasar por el antiguo contrato de bloques mono de V3.')

# 4
title(3,'Compilación y recursos')
table(['Sección','Adaptación','Motivo'],[
    ['settings.gradle.kts','Incluye sound-engine y audioToHaptics. El módulo de modelos es condicional.','Un solo proyecto que compila aunque Demucs no se haya entregado.'],
    ['Gradle raíz','Plugins Android library y serialización Kotlin alineados con la app.','Compatibilidad de las bibliotecas con AGP 9.'],
    ['app/build.gradle.kts','minSdk 26, Java 17, dependencias a módulos y ONNX sin comprimir.','Cumplir requisitos existentes de la biblioteca musical.'],
    ['sound-engine','Coroutines 1.9.0 y LiteRT 1.4.2 con la API Interpreter.','Reutilizar el runtime de V3 y evitar duplicados TensorFlow Lite.'],
    ['audioToHaptics','Retiro del plugin Kotlin Android y kotlinOptions antiguos.','Usar Kotlin incorporado en AGP 9, conservando Java 17.'],
],[100,205,WIDTH-305])
h('Resolución del conflicto entre modelos YAMNet')
add('La app anterior y el módulo recibido contenían un archivo llamado yamnet.tflite. La implementación anterior esperaba categorías como salida; el equipo cotidiano utiliza la salida de embeddings de 1024 componentes y un segundo clasificador. No son intercambiables por compartir el nombre.')
add('Se retiró el yamnet.tflite del directorio de assets de la copia de la app. El APK recibe el archivo original de sound-engine, junto con head.tflite, clases.txt y contexts.json. Los archivos de ese módulo no fueron renombrados ni reentrenados.')
h('Paquete opcional musical')
add('settings.gradle.kts incluye audioToHapticsModels únicamente si existe models/htdemucs_6s.onnx. Así se conserva la comprobación original de SHA-256 cuando el archivo se entregue, sin bloquear la compilación actual ni simular que el modelo está instalado.')
add('La obtención de dependencias por Gradle durante la compilación es distinta de descargar modelos al usar la app. Esta integración no generó ni descargó Demucs.','small')

# 5
title(4,'Motor cotidiano: funcionamiento')
story.append(Pipeline(['Micrófono: audio mono a 16 000 Hz','Ventanas de 15 600 muestras; avance de 7 800','YAMNet: vector de 1 024 características','head.tflite: probabilidades para siete clases','Reglas del contexto + confirmación + antirrebote','SoundEvent: clase, confianza, prioridad y contexto']))
add('Cada ventana representa aproximadamente 0,975 segundos y avanza 0,4875 segundos. El motor no clasifica solo el nivel de volumen: YAMNet extrae características del sonido y el clasificador entrenado produce las probabilidades de las clases del proyecto.')
h('Reglas conservadas')
add('Una clase debe estar habilitada en el contexto y superar su umbral. El motor comprueba el nivel mínimo configurado y exige que la misma clase aparezca en dos ventanas válidas consecutivas. Limita las alertas repetidas de una misma clase con un antirrebote de tres segundos.')
add('La integración consume los eventos ya confirmados. No vuelve a aplicar el umbral global 0,35 ni el intervalo global del coordinador anterior, porque eso introduciría una segunda política distinta de las reglas del equipo.')
h('Información mostrada')
add('Al llegar un evento, se presenta la clase con un nombre legible, su confianza, prioridad y contexto. También se conserva un historial de hasta 30 detecciones, del que la pantalla muestra las ocho más recientes.')
add('El motor calcula un nivel acústico estimado con un ajuste pendiente de calibración por teléfono. La interfaz informa esa limitación; no lo presenta como una medición fiable de distancia.','small')

# 6
title(5,'Contextos y clases disponibles')
add('CatalogoCotidianoEquipo lee los mismos contexts.json y clases.txt que usa SoundEngine. Una clase configurada solo se muestra como disponible si aparece entre las etiquetas del clasificador. No se añadieron capacidades cambiando textos de la interfaz.')
ctx=json.loads((ROOT/'sound-engine/src/main/assets/contexts.json').read_text(encoding='utf-8-sig'))['contexts']
trained=set((ROOT/'sound-engine/src/main/assets/clases.txt').read_text(encoding='utf-8-sig').split())
labels={'sirena':'Sirena','claxon':'Claxon','alarma_humo':'Alarma de humo','timbre':'Timbre','llanto_bebe':'Llanto de bebé','vidrio_roto':'Vidrio roto','maquinaria':'Maquinaria','ladrido':'Ladrido','nombre_usuario':'Nombre del usuario'}
rows=[]
for name in ['hogar','urbano','social','industrial']:
    for clase,rule in ctx[name].items():
        rows.append([name.capitalize(),labels[clase],str(rule['prioridad']),f"{rule['umbral']*100:.0f} %",'Disponible' if clase in trained else 'Pendiente'])
table(['Contexto','Sonido','Prior.','Umbral','Modelo'],rows,[70,158,44,55,WIDTH-327])
add('<b>Clases entrenadas:</b> claxon, fondo, ladrido, llanto_bebe, maquinaria, sirena y vidrio_roto. Fondo no se presenta como alerta de un contexto. Alarma de humo, timbre y nombre del usuario siguen pendientes de entrenamiento.')
note('El contexto <b>Social</b> del modelo recibido permite detectar sirena; no se cambió para convertirlo en el antiguo perfil social del firmware. Son configuraciones distintas y la interfaz las separa.')

# 7
title(6,'Sesiones y ciclo de vida')
h('Inicio de la escucha')
add('La pantalla solicita permiso de micrófono. Si se concede mientras Cotidiano está visible y la aplicación está en un estado activo, el coordinador puede iniciar una sesión. Se evita iniciar dos sesiones a la vez o comenzar la escucha mientras se realiza un análisis musical.')
add('Dentro de un Mutex se crea el motor del equipo en un hilo de trabajo, se selecciona el contexto y se registran los colectores de eventos y log antes de llamar a start. Esto importa porque el flujo de eventos no conserva un historial para suscriptores tardíos.')
h('Cambio de contexto')
add('Elegir Hogar, Urbano, Social o Industrial detiene la sesión actual, invalida eventos anteriores y actualiza los casos visibles. El usuario pulsa de nuevo Iniciar escucha. Esta decisión evita modificar el mapa de reglas del motor mientras una inferencia lo está leyendo en otro hilo.')
h('Cancelación y liberación')
add('La sesión se mantiene suspendida hasta cancelarse. Al salir de Cotidiano o detener la escucha, la tarea se cancela y el bloque finally espera releaseAndJoin en un contexto NonCancellable. El Mutex impide que la siguiente sesión abra otro micrófono antes de que termine esa limpieza.')
table(['Mecanismo','Función'],[
    ['exclusividadEscucha','Serializa las sesiones y la liberación de recursos.'],
    ['versionEscucha','Descarta resultados de una sesión que ya se detuvo.'],
    ['CoroutineStart.UNDISPATCHED','Registra los colectores antes de arrancar la captura.'],
    ['NonCancellable + Dispatchers.Default','Completa el cierre nativo aun si la pantalla canceló la sesión.'],
],[190,WIDTH-190])
add('El micrófono utilizado es el de SoundEngine. La ruta activa integrada no abre simultáneamente el AudioRecord del adaptador antiguo EntradaAudioAndroid. Al detener la pantalla también se detienen las señales y las vistas previas administradas por la aplicación.')

# 8
title(7,'Alertas y conexión háptica')
add('Se conservó ConexionEntornoBle y los comandos de los firmware incluidos. El coordinador traduce únicamente las clases que ya tienen una correspondencia existente y verifica el perfil de firmware elegido por el usuario antes de transmitir.')
table(['Clase detectada','Comando','Perfil admitido','Si no se puede enviar'],[
    ['ladrido','DOG','Social','Patrón existente en el teléfono.'],
    ['llanto_bebe','BABY','Social','Patrón existente en el teléfono.'],
    ['sirena','SIREN','Alerta bicicleta','Patrón existente en el teléfono.'],
    ['claxon','HORN','Alerta bicicleta','Patrón existente en el teléfono.'],
    ['maquinaria','Sin comando','No definido','Aviso visual, sin patrón inventado.'],
    ['vidrio_roto','Sin comando','No definido','Aviso visual, sin patrón inventado.'],
],[95,65,100,WIDTH-260])
h('Contexto acústico y firmware son independientes')
add('El contexto decide qué sonidos intenta reconocer el modelo y con qué reglas. El perfil de firmware indica qué órdenes entiende el ESP32. Por ejemplo, Hogar puede detectar un ladrido, pero enviar DOG solo tiene sentido si está seleccionado el firmware Social.')
h('Indicadores de Inicio')
add('Bluetooth muestra si el adaptador del teléfono está activado, desactivado, no disponible o pendiente de permiso. Háptica refleja el flujo de conexión del coordinador: desconectada, conectando o conectada. El estado positivo requiere descubrir el servicio y la característica de escritura esperados.')
add('Los firmware comparten nombre y UUID. El descubrimiento GATT no identifica automáticamente cuál está instalado ni garantiza que un actuador haya vibrado. La confirmación de escritura tampoco mide una ejecución física. No se ensayó el brazalete en esta entrega.')
add('Servicio: 12345678-1234-1234-1234-1234567890ab<br/>Comandos: abcdefab-1234-5678-1234-abcdefabcdef<br/>Nombre anunciado: VIBRAFIL_ESP32','small')

# 9
title(8,'Biblioteca musical recibida')
add('Se reutilizó la implementación Android de AudioToHaptics, con su decodificación, ONNX Runtime y procesamiento DSP. Su código de producción permanece idéntico al recibido. El siguiente flujo describe lo que ejecuta la biblioteca cuando están presentes todos los recursos; no acredita que se haya ejecutado aquí el análisis completo.')
story.append(Pipeline(['Archivo local por URI: M4A, MP3 o WAV compatible','Decodificación y remuestreo a 44,1 kHz','HTDemucs: separación de fuentes','Voz / guitarra: Basic Pitch  |  batería: detector de golpes','Síntesis: tres WAV mono PCM16 sincronizados','Resultados: WAV, MIDI, eventos CSV y manifest.json']))
h('Qué representan los canales')
add('Voz, guitarra y percusión se convierten en señales con una portadora fija por actuador. Se representan la duración y el ritmo de los eventos y, según configuración, su intensidad. La altura de una nota no se convierte en una frecuencia de vibración distinta para cada nota.')
h('Recursos presentes y ausentes')
add('Basic Pitch está incluido como basic_pitch_nmp.onnx. Falta htdemucs_6s.onnx, necesario para separar los instrumentos. modelStatus permite mostrar ese estado antes de ofrecer un análisis que no puede completarse.')
note('La integración contiene la biblioteca y su ruta de ejecución, pero <b>no generó, descargó ni sustituyó Demucs</b>. La ausencia del modelo se conserva como pendiente explícito.')

# 10
title(9,'Conexión musical con la app')
h('Por qué se usa la API por URI')
add('La interfaz anterior AlgoritmoMusica.procesar recibía fragmentos de audio mono. La biblioteca recibida procesa una canción completa mediante AudioToHaptics.process(uri), administra su decodificación y separa fuentes. Se conectó esa API al selector de archivos existente, en lugar de modificar la biblioteca para adaptarla al contrato anterior.')
h('Condiciones del botón Analizar música')
add('Se exige que el reproductor tenga un archivo listo, que ambos modelos estén incluidos, que el usuario haya activado la configuración de prueba y que no haya otro análisis en curso. En esta entrega el modelo faltante mantiene el botón deshabilitado y se muestra la explicación.')
h('Configuración, progreso y errores')
add('Los parámetros oficiales de actuadores siguen pendientes. El usuario puede seleccionar explícitamente HapticsConfig.testValues: 150/200/250 Hz y 44,1 kHz. Son los valores de laboratorio originales, sin confirmación para el hardware. También puede pedir que se guarden las fuentes separadas.')
add('El coordinador ejecuta una tarea cancelable, evita dos análisis simultáneos y muestra etapas en español y porcentaje. Controla errores de configuración, modelo ausente, formato, procesamiento y memoria. Un contador de versión evita que un análisis cancelado actualice una selección posterior.')
h('Resultados y reproducción')
add('La pantalla contempla los tres ActuatorOutput: cuenta de eventos, portadora, WAV, MIDI cuando corresponda y CSV. También muestra advertencias y la carpeta de resultados. La vista previa escucha los archivos mediante MediaPlayer, como la demo original. Al empezar una vista previa se detiene la escucha ambiental.')
note('No se conectaron los tres canales al secuenciador de notas antiguo: ese secuenciador descarta eventos simultáneos y no representa canales independientes. Tampoco se inventó una transmisión de WAV por el protocolo BLE actual.')

# 11
title(10,'Interfaz cotidiana: evidencia')
add('Las capturas se obtuvieron durante las pruebas instrumentadas. Las tarjetas proceden del catálogo del algoritmo: disponibilidad, prioridad y umbral. Las clases sin patrón de firmware tienen una explicación visual; las pendientes no se anuncian como reconocibles.')
screenshots(['cotidiano_hogar.png','cotidiano_industrial.png'],[
    '<b>Hogar.</b> Llanto de bebé y vidrio roto disponibles; nombre del usuario y timbre pendientes. El botón de prueba representa un patrón manual, no una detección.',
    '<b>Industrial.</b> La lista cambia con el contexto. Maquinaria se presenta como detección visual porque el firmware recibido no tiene un comando para ella.',
])
story.append(Spacer(1,12))
add('La sección del dispositivo permite elegir el firmware de salida independientemente del contexto. La intensidad del teléfono ajusta los patrones locales; la intensidad del brazalete depende de su firmware.','small')

# 12
title(11,'Inicio y Música: evidencia')
add('La navegación conserva Inicio, Música y Cotidiano. No reaparece el juego musical eliminado en PrototipoV3. La pantalla de Música distingue reproducción normal, presencia de modelos y configuración para analizar.')
screenshots(['inicio.png','musica_modelos.png'],[
    '<b>Inicio.</b> Acceso a las experiencias y al estado de conexión Bluetooth/háptica dentro del panel desplazable.',
    '<b>Música.</b> Basic Pitch incluido, Demucs ausente y configuración de prueba identificada. El análisis está deshabilitado por sus requisitos pendientes.',
])
story.append(Spacer(1,12))
add('La captura musical corresponde a la app compilada sin Demucs. No se utilizaron imágenes de resultados ficticios para aparentar que ya se habían separado instrumentos.','small')

# 13
title(12,'Cambios directos en los equipos')
h('1. SoundEngine.kt: releaseAndJoin')
add('<b>Ubicación:</b> sound-engine/src/main/java/com/indoamerica/haptica/soundengine/SoundEngine.kt, método añadido junto a stop y release.')
code('suspend fun releaseAndJoin() {\n    val actual = job\n    actual?.cancelAndJoin()\n    job = null\n    scope.cancel()\n    yamnet.close()\n    head.close()\n}')
add('<b>Motivo:</b> release cancelaba la tarea y cerraba los intérpretes inmediatamente. Una lectura o inferencia podía seguir terminando. El nuevo método espera el final de la tarea antes de liberar los modelos y permite serializar el cambio de sesión. La app no llama a stop antes de este método, para conservar la referencia que debe esperar.')
add('<b>Alcance:</b> gestión de recursos. No modifica inferencia, ventanas, pesos, clases, umbrales, prioridades ni antirrebote. Los métodos originales siguen disponibles.')
h('2. ModelIntegrationTest.kt: datos de referencia')
add('<b>Ubicación:</b> audioToHaptics/src/test/kotlin/com/tesis/audiotohaptics/ModelIntegrationTest.kt, inicio de basicPitchKotlinMatchesNumpyReferenceAndPrototype.')
add('Se añadió una comprobación Assume.assumeTrue de que exista al menos una pareja de stem WAV y referencia CSV. Sin esos archivos, la prueba original saltaba sus entradas, producía un informe vacío y terminaba fallando. Ahora se registra como omitida por datos ausentes.')
add('Cuando las referencias existen se conservan las comparaciones numéricas originales. No cambió el modelo Basic Pitch ni su código de producción. Los otros dos cambios en los módulos son ajustes de sus scripts Gradle, explicados en la página 4.')
add('Las diferencias exactas, archivo por archivo, están en docs/auditoria/. El registro ampliado está en docs/CAMBIOS_INTEGRACION.md.','small')

# 14
title(13,'Pruebas y conservación de fuentes')
table(['Grupo','Superadas','Omitidas','Fallos'],[
    ['Aplicación / JVM','11','0','0'],['Biblioteca musical / JVM','25','6','0'],['Emulador Android 17 / API 37','7','0','0'],['Total','43','6','0'],
],[WIDTH-195,65,65,65])
h('Qué se comprobó en el emulador')
add('Se verificó la coincidencia del catálogo con los assets; una inferencia real YAMNet con salida de 1024 componentes; la salida de siete probabilidades finitas de head.tflite; y dos sesiones consecutivas de micrófono con cierre seguro. También se comprobó la navegación entre contextos y el aviso del modelo musical ausente.')
add('La inferencia utiliza silencio para comprobar carga, dimensiones y ejecución. No es una medición de exactitud acústica. Las pruebas musicales disponibles validan configuración, DSP, síntesis y errores con recursos locales o sintéticos.')
h('Por qué se omitieron seis pruebas')
add('Faltan recursos externos para la referencia numpy del remuestreador, la comparación Basic Pitch, la comparación Demucs, la percusión de una batería real y los dos pipelines de canciones completas. No se contaron como pruebas superadas.')
h('Auditoría reproducible')
table(['Comparación de módulos','Resultado'],[
    ['Archivos comparados','47'],['Archivos idénticos por bytes','43'],['Archivos modificados','4: dos Gradle, un método de cierre y una condición de prueba'],['Otros cambios en producción','Ninguno detectado por el script'],
],[200,WIDTH-200])
add('scripts/verificar_fuentes.py calcula SHA-256 contra las carpetas originales y genera JSON y diffs. Todas las fuentes de producción de audioToHaptics y los assets de los motores permanecen idénticos. Esta auditoría de 47 archivos corresponde a los módulos copiados, no a todos los archivos de la aplicación.')

# 15
title(14,'Uso y reproducción de la entrega')
h('Compilar e instalar')
add('Abrir V3Integracion1.0 en Android Studio. El proyecto requiere JDK 21 para Gradle, SDK Android 37 y 35 y Android 8/API 26 o superior para instalar. El código de las bibliotecas se compila con objetivo Java 17.')
code('.\\gradlew.bat :app:assembleDebug\n.\\gradlew.bat :app:testDebugUnitTest\n.\\gradlew.bat :audioToHaptics:testDebugUnitTest\n.\\gradlew.bat :app:connectedDebugAndroidTest')
add('APK generado: app/build/outputs/apk/debug/app-debug.apk. Las pruebas conectadas requieren un emulador o teléfono con depuración habilitada. No se necesita generar Demucs para compilar la entrega actual.')
h('Usar Cotidiano')
add('1. Abrir Cotidiano y seleccionar un contexto.<br/>2. Revisar los sonidos disponibles y los pendientes.<br/>3. Pulsar Iniciar escucha y conceder el permiso de micrófono.<br/>4. Para usar el brazalete, elegir su firmware y conectar la háptica.<br/>5. Cambiar de contexto detiene la sesión; iniciar de nuevo para el nuevo modo.<br/>6. Probar patrón ejecuta una demostración manual y no añade una detección al historial.')
h('Usar Música ahora y después')
add('Ahora se puede seleccionar y reproducir un archivo local. El panel explica la ausencia de Demucs. Si el equipo entrega el modelo validado, colocarlo en models/htdemucs_6s.onnx y recompilar: el módulo opcional verificará su SHA-256 antes de empaquetarlo.')
add('La huella esperada es:<br/><font name="Mono" size="7.5">16ebcff7c392fc1a21bd2574ea686d20c0ffd9abfd7dcb56680359cb5450c0e0</font>','small')
add('Con ambos modelos presentes seguirá siendo necesario elegir explícitamente los parámetros de prueba o incorporar una configuración de hardware confirmada en una etapa posterior. Copiar el modelo al teléfono sin recompilar esta aplicación no activa el paquete de assets.')

# 16
title(15,'Mapa técnico y pendientes')
table(['Archivo o carpeta','Responsabilidad'],[
    ['app/.../integracion/RegistroAlgoritmos.kt','Fábricas de las bibliotecas reales.'],
    ['app/.../integracion/IntegracionViewModel.kt','Sesiones, eventos, salida cotidiana, API musical y vista previa.'],
    ['app/.../integracion/CatalogoCotidianoEquipo.kt','Catálogo de interfaz derivado de los assets.'],
    ['app/.../ui/screens/PanelesEquipos.kt','Paneles cotidiano y musical.'],
    ['app/.../ui/screens/ExperienceScreen.kt','Navegación y reproductor de la app.'],
    ['sound-engine/src/main/','Motor cotidiano y sus cuatro assets.'],
    ['audioToHaptics/src/main/','Pipeline musical y Basic Pitch, conservados.'],
    ['docs/CAMBIOS_INTEGRACION.md','Registro de qué cambió, dónde y por qué.'],
    ['docs/VALIDACION.md y resultado_pruebas.json','Resultados y límites de las comprobaciones.'],
    ['docs/auditoria/','Hashes, resumen y diferencias de fuentes.'],
],[250,WIDTH-250])
add('En las rutas app/... se omite el prefijo src/main/java/com/rommelguerra/prototipomusichaptic para facilitar la lectura. El script que generó este informe está en scripts/generar_informe_pdf.py.','small')
h('Pendientes que se conservaron expresamente')
add('Demucs sigue ausente; alarma de humo, timbre y nombre del usuario siguen sin entrenar; maquinaria y vidrio roto no tienen un comando de firmware nuevo; los parámetros físicos de actuadores siguen sin confirmar; y no existe transmisión musical de tres canales al ESP32.')
add('Las siguientes etapas requieren recursos y pruebas adicionales: modelo validado, teléfono físico para memoria y tiempos, firmware compatible con canales musicales y verificación de latencia y respuesta mecánica. Nada de ello se presenta como completado por compilar una APK o superar pruebas de emulador.')
note('<b>Resultado verificable:</b> una aplicación compilable que usa el motor cotidiano recibido, contiene la biblioteca musical y muestra con precisión sus requisitos pendientes. Los cambios de integración están separados de los algoritmos y respaldados por pruebas y una comparación de fuentes.')

def page_frame(c,doc):
    c.saveState()
    if doc.page>1:
        c.setFillColor(GRAY);c.setFont('Arial',8)
        c.drawString(46,A4[1]-31,'V3INTEGRACION1.0  /  INFORME DE UNIFICACIÓN')
        c.setStrokeColor(LINE);c.line(46,A4[1]-39,A4[0]-46,A4[1]-39)
    c.setStrokeColor(LINE);c.line(46,36,A4[0]-46,36)
    c.setFillColor(GRAY);c.setFont('Arial',8)
    c.drawString(46,23,'29 de octubre de 2026  |  Documentación de la entrega local')
    c.setFont('Arial-Bold',9);c.drawRightString(A4[0]-46,23,f'{doc.page:02d}')
    c.restoreState()

doc=SimpleDocTemplate(str(PDF),pagesize=A4,rightMargin=46,leftMargin=46,topMargin=57,bottomMargin=49,
    title='Informe detallado de unificación - V3Integracion1.0',author='Documentación técnica de V3Integracion1.0',
    subject='Integración de SoundEngine y AudioToHaptics en una app Android')
doc.build(story,onFirstPage=page_frame,onLaterPages=page_frame)
print(PDF)
