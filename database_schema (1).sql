
DROP TABLE IF EXISTS data_suhu CASCADE;
DROP TABLE IF EXISTS sensor CASCADE;


CREATE TABLE sensor (
    id_sensor SERIAL NOT NULL,
    nama_sensor VARCHAR(100) NOT NULL,
    
    CONSTRAINT pk_sensor PRIMARY KEY (id_sensor)
);

CREATE TABLE data_suhu (
    id_data BIGSERIAL NOT NULL,
    id_sensor INT NOT NULL,
    urutan INT NOT NULL,
    waktu TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    suhu DOUBLE PRECISION NOT NULL,
    
    CONSTRAINT pk_data_suhu PRIMARY KEY (id_data),
    CONSTRAINT fk_data_suhu_sensor FOREIGN KEY (id_sensor) 
        REFERENCES sensor (id_sensor) 
        ON UPDATE CASCADE 
        ON DELETE CASCADE
);


-- Indeks untuk mempercepat pencarian data berdasarkan sensor dan waktu
CREATE INDEX idx_data_suhu_sensor ON data_suhu(id_sensor);
CREATE INDEX idx_data_suhu_waktu ON data_suhu(waktu DESC);
