# Supermarket Inventory Scanner - POC (Prueba de Concepto)

Este proyecto es una Prueba de Concepto (POC) para automatizar el inventario de supermercados utilizando una arquitectura server-side. Consiste en una aplicación nativa de Android acoplada a un backend de Firebase (Firestore, Storage y Cloud Functions en TypeScript) e integrada con la inteligencia artificial de Gemini (modelo `gemini-2.5-flash`) para estructurar información de productos a partir de fotos del empaque de forma asíncrona.

---

## 1. Arquitectura del Sistema y Flujo de Datos

El diseño arquitectónico prioriza la eficiencia en el dispositivo móvil eliminando la inferencia pesada de IA del cliente. La aplicación móvil delega el análisis de imágenes y la estructuración de datos al backend en la nube.

```mermaid
sequenceDiagram
    autonumber
    actor Usuario
    participant Android as App Android (CameraX + ML Kit)
    participant Firestore as Firebase Firestore
    participant Storage as Firebase Storage
    participant CF as Cloud Function (Node.js)
    participant Gemini as Google Gemini API

    Usuario->>Android: Escanea código de barras del producto
    Android->>Firestore: Consulta reactiva (Document ID = código de barras)
    alt Caso A: El producto ya existe
        Firestore-->>Android: Retorna marca, nombre y contenido
        Android->>Usuario: Muestra la información del producto
    else Caso B: El producto es nuevo (No existe)
        Firestore-->>Android: Retorna documento vacío/inexistente
        Android->>Usuario: Transiciona a pantalla de captura fotográfica
        Usuario->>Android: Toma una o más fotos del empaque
        Android->>Storage: Sube imágenes a "/productos/{codigo_barras}/{timestamp}.jpg"
        Android->>Usuario: Muestra estado "PROCESANDO EN COLA" (Escuchando a Firestore)
        Note over Storage, CF: Trigger: onObjectFinalized
        Storage->>CF: Dispara ejecución de la Cloud Function
        CF->>Storage: Descarga todas las fotos del lote en productos/{codigo_barras}/
        CF->>Gemini: Envía imágenes con System Instruction y responseSchema estructurado
        Gemini-->>CF: Retorna JSON estructurado (marca, nombre, contenido)
        CF->>Firestore: Guarda información en la colección "productos" ({codigo_barras})
        Firestore-->>Android: Sincronización en tiempo real (addSnapshotListener)
        Android->>Usuario: Actualiza UI mostrando los datos extraídos automáticamente
    end
```

---

## 2. Componentes del Proyecto

### A. Cliente Android (`/app`)
Construido utilizando prácticas modernas de desarrollo de Android:
- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Arquitectura:** MVVM (Model-View-ViewModel) con flujos reactivos.
- **Escaneo Local:** Google ML Kit Barcode Scanning para la detección instantánea de formatos `EAN_13`, `EAN_8` y `UPC_A`.
- **Cámara:** Integración con Jetpack CameraX para el escaneo de códigos de barras y captura fotográfica en alta resolución.
- **Sincronización:** Firebase Firestore SDK (con escuchas en tiempo real vía `addSnapshotListener`) y Firebase Storage SDK para la subida asíncrona de archivos.
- **Navegación:** Jetpack Navigation3.

### B. Backend / Servidor (`/functions`)
Funciones de backend sin servidor (Serverless) configuradas en Firebase:
- **Lenguaje/Entorno:** TypeScript en Node.js (Firebase Functions v2).
- **Trigger:** Evento `onObjectFinalized` de Firebase Storage que detecta la subida completa de imágenes al directorio del producto.
- **IA Generativa:** SDK Oficial de Google Gen AI (`@google/genai`) utilizando el modelo `gemini-2.5-flash`.
- **Esquema Estricto:** Uso del parámetro `responseSchema` de Gemini para obligar al modelo a retornar un JSON estructurado que se valida y parsea directamente en el backend para evitar deudas técnicas de parseo manual de texto.

---

## 3. Estructura de Archivos del Proyecto

```text
SupermarketScanner/
├── app/                              # Módulo de la aplicación Android
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/caucorp/supermarketscanner/
│   │   │   │   ├── data/             # Fuentes de datos auxiliares
│   │   │   │   ├── model/            # Modelos de datos (Product.kt)
│   │   │   │   ├── theme/            # Configuración de Compose Theme (Material 3)
│   │   │   │   ├── repository/       # Repositorio Firebase (Firestore + Storage)
│   │   │   │   ├── ui/               # Vistas Compose (Scanner, Capture, Detail, etc.)
│   │   │   │   │   └── main/         # Pantalla principal y su ViewModel
│   │   │   │   └── viewmodel/        # Estado global de flujo de pantallas
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
├── functions/                        # Módulo del Backend (Firebase Cloud Functions)
│   ├── src/
│   │   └── index.ts                  # Cloud Function de procesamiento con Gemini
│   ├── package.json
│   └── tsconfig.json
├── firestore.rules                   # Reglas de seguridad de Firestore
├── storage.rules                     # Reglas de seguridad de Firebase Storage
├── firebase.json                     # Configuración de despliegue de Firebase
└── build.gradle.kts                  # Configuración Gradle raíz del proyecto
```

---

## 4. Requisitos Previos

Para ejecutar y probar este proyecto localmente o desplegarlo a producción, se requiere:

- **Para el Cliente Android:**
  - Android Studio Koala (2024.1.1) o superior.
  - Android SDK 36 (compilación y target).
  - Dispositivo físico Android (recomendado para usar CameraX) o emulador con cámara configurada, con nivel de API 24 (Android 7.0) o superior.
- **Para el Backend:**
  - Node.js (versión 18 o 20 recomendada).
  - Firebase CLI instalado globalmente (`npm install -g firebase-tools`).
  - Una cuenta de Firebase con un proyecto activo y plan **Blaze** (requerido para utilizar Cloud Functions y llamadas a APIs externas).
  - Una **API Key de Gemini** (obtenida desde Google AI Studio).

---

## 5. Configuración y Despliegue

### Paso 1: Configurar el Proyecto de Firebase
1. Crea un proyecto en la [Consola de Firebase](https://console.firebase.google.com/).
2. Habilita **Cloud Firestore** en modo de prueba o producción.
3. Habilita **Firebase Storage**.
4. Agrega una aplicación Android a tu proyecto de Firebase usando el ID de paquete `com.caucorp.supermarketscanner`.
5. Descarga el archivo `google-services.json` y colócalo en el directorio del proyecto Android: `SupermarketScanner/app/google-services.json`.

### Paso 2: Desplegar el Backend (Cloud Functions y Reglas)
1. Inicia sesión en Firebase CLI desde tu terminal:
   ```bash
   firebase login
   ```
2. Asocia el proyecto local con tu ID de proyecto de Firebase:
   ```bash
   firebase use --add
   ```
3. Configura la clave API de Gemini como variable de entorno secreta en Firebase para la Cloud Function. Puedes hacerlo a través de la consola o mediante la configuración de secretos:
   ```bash
   firebase functions:secrets:set GEMINI_API_KEY="TU_GEMINI_API_KEY"
   ```
4. Navega al directorio de `functions` e instala las dependencias necesarias:
   ```bash
   cd functions
   npm install
   cd ..
   ```
5. Despliega las reglas de base de datos, almacenamiento y las Cloud Functions:
   ```bash
   firebase deploy
   ```

### Paso 3: Ejecutar la Aplicación Android
1. Abre el proyecto raíz `SupermarketScanner` en Android Studio.
2. Asegúrate de que el SDK de Android esté actualizado y sincroniza los archivos de Gradle.
3. Conecta tu dispositivo Android con la depuración USB activada.
4. Presiona **Run** en Android Studio para compilar e instalar la aplicación.

---

## 6. Flujo de Datos Técnico de la Cloud Function

La función `processSupermarketProduct` implementa un esquema estricto de generación estructurada con la API de Gemini:

```typescript
// System Instruction enviada a Gemini
const systemInstruction = "Eres un sistema automatizado de extracción de datos para inventarios de supermercado. Tu único objetivo es analizar una o más imágenes de un empaque de producto y extraer de forma precisa tres atributos obligatorios: la marca del fabricante, el nombre específico del producto y su gramaje o volumen neto. Debes ignorar textos promocionales, recetas, códigos internos o instrucciones de uso.";

// Estructura de esquema estricta (JSON Schema) para asegurar tipos
const responseSchema = {
  type: "OBJECT",
  properties: {
    marca: {
      type: "STRING",
      description: "La marca principal y reconocible del fabricante (ej: Soprole, Nestlé, Corcolén). Si no es visible, usar 'Genérico'."
    },
    nombre: {
      type: "STRING",
      description: "Nombre descriptivo del producto comercial, excluyendo la marca (ej: Maní Salado, Leche Entera, Arroz Grado 1)."
    },
    contenido: {
      type: "STRING",
      description: "La cantidad neta indicada en el empaque incluyendo su unidad de medida (ej: 500g, 1L, 250cc, 12 un)."
    }
  },
  required: ["marca", "nombre", "contenido"],
  additionalProperties: false
};
```

Este esquema garantiza que la salida de la Cloud Function no requiere validaciones complejas de strings ni limpieza de bloques Markdown, insertando los datos de forma robusta e inmediata en la colección de Firestore.

Nota: En la implementación actual (`functions/src/index.ts`) se invoca el modelo `gemini-2.5-flash` para optimizar la relación costo/rendimiento del procesamiento por lote.

---

## 7. UX Actual (v2): Flujo No Bloqueante con Cola Lateral

En la implementación actual, el flujo de captura dejó de ser bloqueante.
Luego de confirmar fotos, la aplicación vuelve inmediatamente al escáner y el procesamiento en backend continúa de forma asíncrona, permitiendo escanear nuevos productos en paralelo.

### 7.1 Flujo de Interacción Actual

1. El usuario escanea un código de barras.
2. Si el producto existe en Firestore, se muestra su detalle inmediatamente.
3. Si el producto no existe, se abre la vista de captura fotográfica.
4. Al presionar **Aceptar** (con al menos una foto):
   - se crea/actualiza el item en cola,
   - se ejecuta una transición visual de miniatura hacia el card de cola,
   - y la app vuelve al escáner sin esperar la respuesta de Gemini.
5. Cloud Functions procesa imágenes, consulta Gemini y actualiza Firestore.
6. La cola lateral refleja estados en tiempo real hasta completar el producto.

### 7.2 Cola Lateral (Parte Superior Derecha)

- La cola se muestra como una lista vertical compacta en la zona superior derecha.
- Cada item usa la primera foto capturada como miniatura.
- Estados visuales:
  - **Loading/Processing:** indicador de carga.
  - **Ready:** check de completado.
  - **Error:** indicador de error.
- La lista tiene scroll interno y está diseñada para no bloquear la operación del escáner.

### 7.3 Comportamiento al Seleccionar un Item de Cola

- Si el item está en **loading/processing**:
  - se abre la vista de procesamiento con botón **Ocultar**,
  - si la respuesta llega mientras está abierta, transiciona automáticamente a detalle.
- Si el item está en **ready**:
  - se abre la vista de detalle con dos acciones:
    - **Ocultar:** vuelve a la vista anterior y mantiene el item en cola.
    - **Cerrar:** vuelve a la vista anterior y elimina el item de la cola.

### 7.4 Ajustes de UI en Captura

- **Cancelar:** botón circular rojo con ícono `X`.
- **Aceptar:** botón circular con check:
  - verde cuando hay al menos una foto,
  - gris cuando no hay fotos.
- Se usa `navigationBarsPadding()` para evitar superposición con la barra de navegación del sistema en dispositivos como Xiaomi.

### 7.5 Transición Visual de Encolado (Pixel-Perfect)

Al confirmar fotos, la primera miniatura se anima (escala + desplazamiento) hacia el card correspondiente del producto recién agregado en la cola, usando coordenadas reales de layout para alineación visual precisa.
