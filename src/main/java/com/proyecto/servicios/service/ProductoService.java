package com.proyecto.servicios.service;

import com.proyecto.servicios.model.producto.ProductoListResponse;

public interface ProductoService {

    /**
     * Consume el servicio externo para obtener el listado de productos disponibles.
     * Utiliza autenticación mediante Bearer Token obtenido de la configuración.
     *
     * @return Respuesta estructurada con la lista de productos.
     */
    ProductoListResponse obtenerListaProductos();
}
