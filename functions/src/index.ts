import { onObjectFinalized } from "firebase-functions/v2/storage";
import * as logger from "firebase-functions/logger";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { GoogleGenAI } from "@google/genai";

initializeApp();
const db = getFirestore();

export const processSupermarketProduct = onObjectFinalized({
  region: "us-east1",
  cpu: 1,
  memory: "256MiB",
  maxInstances: 10
}, async (event) => {
  const filePath = event.data.name; // Format: productos/{barcode}/{timestamp}.jpg
  const bucketName = event.data.bucket;

  logger.info(`Archivo finalizado detectado: ${filePath} en el bucket ${bucketName}`);

  // Regex to match "productos/{barcode}/{filename}"
  const match = filePath.match(/^productos\/([^/]+)\/[^/]+$/);
  if (!match) {
    logger.info(`El archivo no pertenece al directorio de escaneo de productos: ${filePath}`);
    return;
  }

  const barcode = match[1];
  logger.info(`Código de barras extraído: ${barcode}`);

  // 1. Check Firestore first to see if the product details are already populated
  // This acts as a debounce/idempotency guard if multiple images are uploaded concurrently.
  const docRef = db.collection("productos").doc(barcode);
  const docSnap = await docRef.get();

  if (docSnap.exists) {
    const data = docSnap.data();
    if (data && data.marca && data.nombre && data.contenido) {
      logger.info(`El producto ${barcode} ya está procesado y estructurado en Firestore. Omitiendo.`);
      return;
    }
  }

  // 2. Fetch all images in the folderproductos/{barcode}/
  const storage = getStorage();
  const bucket = storage.bucket(bucketName);
  const [files] = await bucket.getFiles({ prefix: `productos/${barcode}/` });

  const imageFiles = files.filter(file => 
    file.name.toLowerCase().endsWith(".jpg") || 
    file.name.toLowerCase().endsWith(".jpeg") || 
    file.name.toLowerCase().endsWith(".png")
  );

  if (imageFiles.length === 0) {
    logger.error(`No se encontraron imágenes válidas en el directorio productos/${barcode}/`);
    return;
  }

  logger.info(`Procesando lote de ${imageFiles.length} imágenes para el producto ${barcode}`);

  // 3. Download all images and encode them to Base64 in memory
  const imagesBase64 = await Promise.all(
    imageFiles.map(async (file) => {
      const [buffer] = await file.download();
      const mimeType = file.metadata?.contentType || "image/jpeg";
      return {
        inlineData: {
          data: buffer.toString("base64"),
          mimeType: mimeType
        }
      };
    })
  );

  // 4. Initialize Google Gen AI SDK
  // It automatically looks up process.env.GEMINI_API_KEY, but we can also retrieve it or pass it.
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    logger.error("La variable de entorno GEMINI_API_KEY no está configurada.");
    throw new Error("Missing GEMINI_API_KEY env variable");
  }

  const ai = new GoogleGenAI({ apiKey });

  // 5. Call Gemini using a structured JSON schema configuration
  const systemInstruction = "Eres un sistema automatizado de extracción de datos para inventarios de supermercado. Tu único objetivo es analizar una o más imágenes de un empaque de producto y extraer de forma precisa tres atributos obligatorios: la marca del fabricante, el nombre específico del producto y su gramaje o volumen neto. Debes ignorar textos promocionales, recetas, códigos internos o instrucciones de uso.";

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

  try {
    logger.info(`Invocando API de Gemini para el producto ${barcode}...`);
    const response = await ai.models.generateContent({
      model: "gemini-2.0-flash", // Use gemini-2.0-flash as specified
      contents: [
        ...imagesBase64,
        "Analiza estas imágenes de empaque y extrae marca, nombre y contenido."
      ],
      config: {
        systemInstruction: systemInstruction,
        responseMimeType: "application/json",
        responseSchema: responseSchema
      }
    });

    const text = response.text;
    if (!text) {
      throw new Error("Respuesta vacía recibida desde la API de Gemini");
    }

    logger.info(`Respuesta cruda recibida de Gemini: ${text}`);
    const data = JSON.parse(text);

    // Validate structured output structure
    if (!data.marca || !data.nombre || !data.contenido) {
      throw new Error(`Los datos de Gemini no cumplen el esquema: ${text}`);
    }

    // 6. Write final structured product data to Firestore
    await docRef.set({
      marca: data.marca,
      nombre: data.nombre,
      contenido: data.contenido,
      createdAt: docSnap.exists ? (docSnap.data()?.createdAt || new Date()) : new Date(),
      updatedAt: new Date()
    }, { merge: true });

    logger.info(`Estructuración exitosa del producto ${barcode} guardada en Firestore.`);

  } catch (error: any) {
    logger.error(`Error en el procesamiento del producto ${barcode}:`, error);
    throw error;
  }
});
