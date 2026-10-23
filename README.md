# Editor de Texto

Editor con interfaz grafica en Swing: abrir, guardar, deshacer, rehacer, buscar, reemplazar y estadisticas del documento. Sin dependencias externas.

Tres defectos, y el mas grave convierte el guardado en el momento mas peligroso del programa.

## Defecto 1: guardar podia destruir el archivo

```java
Files.writeString(archivo, documento.getContenido(), StandardCharsets.UTF_8);
```

Sin opciones, `writeString` abre el archivo con `CREATE + TRUNCATE_EXISTING + WRITE`. **El truncado ocurre antes de escribir el primer byte.** Entre ese instante y el final de la escritura, el archivo del disco esta vacio o a medias.

Si el proceso muere ahi — se corta la luz, se acaba el disco, el usuario mata la aplicacion — no queda ni el contenido nuevo ni el viejo. Se pierde el documento entero **por guardarlo**.

Y ese es el punto: en un editor de texto, "guardar" es la operacion que el usuario ejecuta precisamente para *no* perder su trabajo. Que sea el momento mas peligroso es lo contrario de lo que promete.

La correccion es la de siempre:

1. escribir todo en un archivo temporal, **en la misma carpeta**
2. renombrarlo encima del original con `ATOMIC_MOVE`

Un renombrado dentro del mismo sistema de archivos es atomico: el nombre apunta al archivo viejo o al nuevo, nunca a medias. Cualquier fallo antes del renombrado deja el original intacto y solo ensucia un temporal, que un `finally` limpia.

El temporal va en la misma carpeta a proposito: `ATOMIC_MOVE` solo funciona dentro del mismo sistema de archivos, y `/tmp` suele estar en otro. Si el sistema no lo admite — algunos de red no — se cae a un reemplazo normal, que sigue siendo mejor: la ventana de peligro pasa de "toda la escritura" a "el instante del reemplazo".

**Una nota de honestidad:** la prueba comprueba lo observable — que el contenido queda bien, que no se deja ningun temporal, y que un guardado fallido deja el archivo anterior intacto. La ventana de perdida en si no se puede provocar desde una prueba unitaria: hace falta matar el proceso en el instante justo. Es un defecto razonado, no medido, y el README lo dice en vez de disimularlo.

## Defecto 2: deshacer hasta el estado guardado seguia marcando "modificado"

```java
public boolean deshacer() {
    ...
    modificado = true;    // <- siempre
}
```

```
guardar            -> modificado = false
escribir " mundo"  -> modificado = true
deshacer           -> contenido = "hola", identico al del disco
                      modificado = true
```

El editor preguntaba *"¿guardar los cambios?"* al cerrar un documento que no tiene ninguno.

Parece cosmetico y no lo es: **entrena al usuario a decir que si por costumbre**, y el dia que la pregunta importa la contesta sin leerla.

La correccion es un numero de version. Cada cambio lo sube, cada deshacer lo baja, cada rehacer lo vuelve a subir, y guardar anota en que version quedo el disco. `estaModificado()` es una comparacion de dos enteros — sin recorrer el texto, que en un documento de megabytes importa porque la barra de estado lo consulta en cada pulsacion.

Hay un caso que hay que pensar: si se escribe algo nuevo estando **por debajo** de la version guardada, la rama de rehacer que llevaba a ella se borra y esa version deja de ser alcanzable. El documento queda modificado hasta que se vuelva a guardar, que es lo correcto. Tiene su prueba.

## Defecto 3: el historial guardaba el documento entero en cada cambio

```java
private void cambiarA(String nuevo) {
    paraDeshacer.push(contenido);   // una copia completa por pulsacion
    ...
}
```

Con el limite de 100 pasos, escribir cien caracteres en un documento de 5 MB dejaba **500 MB** retenidos en el historial. Y no es un caso extremo: 5 MB son unas 1500 paginas de texto, y cien pulsaciones son media frase.

**El coste era proporcional al tamaño del documento cuando deberia serlo al tamaño del cambio.** Escribir una letra no tiene por que costar lo que mide el archivo.

El historial ahora guarda deltas: un `Cambio(posicion, borrado, insertado)`. Para `setContenido`, el delta se calcula recortando el prefijo y el sufijo comunes:

```java
static Cambio entre(String viejo, String nuevo) {
    int prefijo = 0;
    while (prefijo < minimo && viejo.charAt(prefijo) == nuevo.charAt(prefijo)) prefijo++;
    int sufijo = 0;
    while (sufijo < minimo - prefijo && viejo.charAt(...) == nuevo.charAt(...)) sufijo++;
    return new Cambio(prefijo, viejo.substring(...), nuevo.substring(...));
}
```

La prueba lo mide:

```java
String largo = "x".repeat(1_000_000);
DocumentoTexto documento = new DocumentoTexto(largo);
for (int i = 0; i < 100; i++) documento.anexar("a");

assertEquals(100, documento.pesoDelHistorial());   // cien caracteres, no cien megabytes
```

El recorte de prefijo y sufijo tiene una trampa: si se pasan de largo, se solapan y los `substring` salen negativos. La condicion `sufijo < minimo - prefijo` lo impide, y hay una prueba de ida y vuelta sobre **todos los pares** de un conjunto de textos elegido para exponerlo — cadenas vacias, repeticiones como `"aa"` y `"abab"`, prefijos y sufijos compartidos.

## Las decisiones de diseno

**Toda la logica vive en `DocumentoTexto`, no en la ventana.** Es lo que permite que el proyecto tenga 48 pruebas sin abrir una sola ventana. Un editor probado a traves de su interfaz grafica seria lento, fragil, y no correria en un servidor sin pantalla.

**El limite del historial descarta los pasos mas viejos, no los mas nuevos.** Deshacer todo no devuelve al original: devuelve tan atras como el limite permita. Es lo correcto — el limite existe justamente para no guardarlo todo — y esta fijado en una prueba para que se vea que es una decision.

**Un cambio que no cambia nada no ensucia el documento.** `setContenido` con el mismo texto, `insertar` de una cadena vacia y `borrar(1, 1)` no tocan el historial ni marcan modificado.

**Un documento vacio tiene cero lineas, no una.** Es una decision; la alternativa — contar los saltos mas uno, que es lo que hacen la mayoria de los editores — es igual de defendible. Lo que importa es que este documentada y probada en vez de ser un accidente del `if` de arriba del metodo.

**`VentanaEditor` lleva `serialVersionUID` y campos `transient`.** No porque tenga sentido serializar una ventana, sino porque `JFrame` es serializable y esa obligacion se hereda sin quererla. Es uno de los precios de extender una clase de Swing en vez de componerla.

## Las 48 pruebas

**`DocumentoTextoTest` (38)**

| grupo | que cubre |
|---|---|
| **Estado guardado** | El defecto: deshacer hasta el disco; rehacer; a cualquier profundidad; la rama inalcanzable; un cambio que no cambia nada |
| **Historial con deltas** | El peso del historial; solo la diferencia; deshacer y rehacer reconstruyen el texto exacto; el limite descarta lo viejo; la rama de rehacer se borra |
| **El calculo del delta** | Prefijo y sufijo comunes; textos iguales; ida y vuelta sobre todos los pares |
| Edicion | Insertar, borrar, anexar, posiciones invalidas, rangos invertidos |
| Busqueda | Primera coincidencia, todas, reemplazo, textos vacios |
| Estadisticas | Caracteres, palabras, lineas |

**`ArchivoTextoTest` (10)** — la ida y vuelta a disco, UTF-8, el guardado atomico sin temporales, el original que sobrevive a un guardado fallido, las rutas nulas.

## Estructura

```
src/main/java/com/fernando/editor/
├── Cambio.java           Record: el delta que guarda el historial.
├── DocumentoTexto.java   Buffer, deshacer/rehacer y estado guardado.
├── ArchivoTexto.java     Lectura y escritura atomica en UTF-8.
├── VentanaEditor.java    Swing; solo conecta la logica con la pantalla.
└── Main.java             Arranca la ventana.
src/test/java/com/fernando/editor/
├── DocumentoTextoTest.java  38 pruebas.
└── ArchivoTextoTest.java    10 pruebas.
```

## Como ejecutar

```bash
mvn package
java -jar target/editor-texto-1.0.0.jar
```

Necesita entorno grafico.

## Como correr las pruebas

```bash
mvn test
```

Las pruebas **no** abren ninguna ventana.
