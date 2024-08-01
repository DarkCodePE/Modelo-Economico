# Modelo Económico Perú

## Descripción del Proyecto

El proyecto "Modelo Económico Perú" es una herramienta diseñada para la previsión y análisis de costos de personal en Telefónica. Su objetivo es gestionar y proyectar costos de empleados, optimizando recursos y facilitando la toma de decisiones financieras.

![image](https://github.com/user-attachments/assets/edcadea0-5443-4c8f-a1e8-ca88569ab9af)

## Funcionalidades Principales

### Funcionalidades Compartidas
- **Historial**:
  - Guardar proyecciones.
  - Deshabilitar períodos.

- **Configuraciones**:
  - Base Externa (Base Exter).
  - Business Case (BC).
  - Parámetros.

- **Reporte**:
  - Reporte de Proyección.
  - Reporte del Planner.
  - Reporte CDG.

### Funcionalidades por Modelo
Cada país tiene su propio conjunto de modelos:
- **Conceptos**: Gestión de conceptos de presupuesto.
- **Nomina**: Administración de nómina y compensaciones.
- **Cuentas**: Manejo de cuentas contables.

## Flujo de Uso de la Herramienta

![image003](https://github.com/user-attachments/assets/2625ceaa-7690-4761-9922-7cd67d544f57)

1. **Previsualización de Costos Proyectados**: Vista preliminar de costos proyectados.
2. **Guardar en Historial**: Guarda proyecciones con un nombre único.
3. **Descargar Archivo de Proyección**: Descarga para revisión.
4. **Revisión en Excel**: Verificación del archivo descargado.
5. **Buscar Proyección en Historial**: Visualiza proyecciones guardadas.
6. **Modificación y Re-proyección**: Modifica y genera nuevas versiones.
7. **Aprobar Proyección**: Valida y aprueba proyecciones.
8. **Descarga de Reporte CD6**: Genera y descarga reporte consolidado.
9. **Entrega a Control de Gestión Local**: La proyección aprobada se entrega al equipo de control de gestión.

## Configuraciones Específicas por País
Cada país tiene configuraciones específicas:
- Clasificación de empleados.
- Conceptos presupuestarios.
- Seniority.
- Quincenas.
- Parámetros temporales.

## Pruebas Integrales
- **Prueba de Integración por País**: Validación de datos y funcionalidad por país.
- **Comparación por País**: Comparación de proyecciones entre países.
- **Formato de Cuadratura**: Formato específico para la cuadratura de datos.
  
![image](https://github.com/user-attachments/assets/101802e3-2564-49e7-9baf-e629ec52b7e4)

## Uso de la Herramienta

### Pasos para Utilizar la Herramienta
1. Selección de períodos.
2. Ingreso de parámetros.
3. Carga de archivos de Business Case y Bases Externas.
4. Desactivación de posiciones.

### Revisión y Aprobación
- Revisión de los archivos en Excel.
- Ajuste y modificación de proyecciones.
- Aprobación y oficialización de proyecciones.

## Arquitectura del Proyecto

El proyecto sigue una arquitectura modular, donde las funcionalidades compartidas y específicas por país están claramente separadas, facilitando la integración y el mantenimiento del sistema.

## Nuevas funcionalidades

Para agregar nuevos features. Por favor, sigue las siguientes pautas para contribuir:

1. Forkea el repositorio.
2. Crea una rama nueva (`git checkout -b feature/nueva-funcionalidad`).
3. Realiza tus cambios y haz commit (`git commit -am 'Añadir nueva funcionalidad'`).
4. Sube tu rama (`git push origin feature/nueva-funcionalidad`).
5. Crea un nuevo Pull Request.

### Flujo de Generación de Reportes

#### Proceso de Generación

1. **Recopilación de Datos**
    - Se recopilan datos de diversas fuentes, incluyendo bases externas y casos de negocio.
    - Los datos se procesan y se estructuran en un formato adecuado para el análisis.

2. **Generación de Reportes**
    - Utilizando los datos recopilados, se generan los reportes en formato Excel.
    - Cada tipo de reporte se genera según las especificaciones y requerimientos del usuario.
    - Se utilizan técnicas de procesamiento paralelo y asincrónico para mejorar la eficiencia y reducir el tiempo de generación.

3. **Almacenamiento y Notificación**
    - Los reportes generados se almacenan en Azure para su acceso y descarga.
    - Se notifica al usuario una vez que los reportes están listos para su d

## Licencia

Este proyecto está licenciado bajo la Licencia Telefónica Hispam.

---

Para más información y detalles sobre el uso de la herramienta, por favor, consulta la documentación adjunta y los manuales de usuario proporcionados por Telefónica.
