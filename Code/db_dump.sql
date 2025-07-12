--
-- PostgreSQL database dump
--

-- Dumped from database version 17.0
-- Dumped by pg_dump version 17.0

-- Started on 2025-07-02 00:50:13 EEST

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 217 (class 1259 OID 26392)
-- Name: environment_variables; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.environment_variables (
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone,
    app_name character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    value text NOT NULL
);


ALTER TABLE public.environment_variables OWNER TO postgres;

--
-- TOC entry 219 (class 1259 OID 26406)
-- Name: function_entity_classes; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.function_entity_classes (
    class_code character varying(255),
    class_name character varying(255) NOT NULL,
    function_entity_id character varying(255) NOT NULL
);


ALTER TABLE public.function_entity_classes OWNER TO postgres;

--
-- TOC entry 220 (class 1259 OID 26413)
-- Name: function_entity_config_code; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.function_entity_config_code (
    config_code character varying(255),
    config_name character varying(255) NOT NULL,
    function_entity_id character varying(255) NOT NULL
);


ALTER TABLE public.function_entity_config_code OWNER TO postgres;

--
-- TOC entry 221 (class 1259 OID 26420)
-- Name: function_entity_db_code; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.function_entity_db_code (
    code_name character varying(255) NOT NULL,
    db_code character varying(255),
    function_entity_id character varying(255) NOT NULL
);


ALTER TABLE public.function_entity_db_code OWNER TO postgres;

--
-- TOC entry 222 (class 1259 OID 26427)
-- Name: function_entity_dependencies; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.function_entity_dependencies (
    dependency character varying(255),
    function_entity_id character varying(255) NOT NULL
);


ALTER TABLE public.function_entity_dependencies OWNER TO postgres;

--
-- TOC entry 223 (class 1259 OID 26432)
-- Name: function_entity_dependency_sources; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.function_entity_dependency_sources (
    dependency_name character varying(255) NOT NULL,
    dependency_source text,
    function_entity_id character varying(255) NOT NULL
);


ALTER TABLE public.function_entity_dependency_sources OWNER TO postgres;

--
-- TOC entry 224 (class 1259 OID 26439)
-- Name: function_entity_env_vars; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.function_entity_env_vars (
    env_var character varying(255),
    function_entity_id character varying(255) NOT NULL
);


ALTER TABLE public.function_entity_env_vars OWNER TO postgres;

--
-- TOC entry 225 (class 1259 OID 26444)
-- Name: function_entity_global_vars; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.function_entity_global_vars (
    function_entity_id character varying(255) NOT NULL,
    global_var character varying(255),
    var_name character varying(255) NOT NULL
);


ALTER TABLE public.function_entity_global_vars OWNER TO postgres;

--
-- TOC entry 226 (class 1259 OID 26451)
-- Name: function_entity_methods; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.function_entity_methods (
    function_entity_id character varying(255) NOT NULL,
    method character varying(255)
);


ALTER TABLE public.function_entity_methods OWNER TO postgres;

--
-- TOC entry 218 (class 1259 OID 26399)
-- Name: function_metrics; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.function_metrics (
    created_at timestamp(6) without time zone NOT NULL,
    failure_count bigint,
    invocation_count bigint,
    last_invoked timestamp(6) without time zone,
    max_execution_time_ms bigint,
    min_execution_time_ms bigint,
    success_count bigint,
    total_execution_time_ms bigint,
    updated_at timestamp(6) without time zone,
    app_name character varying(255),
    function_id character varying(255) NOT NULL,
    function_name character varying(255),
    id character varying(255) NOT NULL,
    user_id character varying(255)
);


ALTER TABLE public.function_metrics OWNER TO postgres;

--
-- TOC entry 227 (class 1259 OID 26456)
-- Name: functions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.functions (
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone,
    app_name character varying(255),
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    path character varying(255) NOT NULL,
    project_id character varying(255),
    user_id character varying(255),
    db_imports_json text,
    imports_json text,
    source text,
    framework character varying(255),
    language character varying(255),
    api_key character varying(255),
    api_key_generated_at timestamp(6) without time zone,
    is_private boolean DEFAULT false NOT NULL
);


ALTER TABLE public.functions OWNER TO postgres;

--
-- TOC entry 228 (class 1259 OID 26465)
-- Name: user_roles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_roles (
    role character varying(255),
    user_id character varying(255) NOT NULL
);


ALTER TABLE public.user_roles OWNER TO postgres;

--
-- TOC entry 229 (class 1259 OID 26470)
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.users (
    active boolean,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone,
    email character varying(255) NOT NULL,
    first_name character varying(255),
    id character varying(255) NOT NULL,
    last_name character varying(255),
    password character varying(255) NOT NULL,
    username character varying(255) NOT NULL
);


ALTER TABLE public.users OWNER TO postgres;

--
-- TOC entry 3499 (class 2606 OID 26398)
-- Name: environment_variables environment_variables_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.environment_variables
    ADD CONSTRAINT environment_variables_pkey PRIMARY KEY (id);


--
-- TOC entry 3503 (class 2606 OID 26412)
-- Name: function_entity_classes function_entity_classes_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_classes
    ADD CONSTRAINT function_entity_classes_pkey PRIMARY KEY (class_name, function_entity_id);


--
-- TOC entry 3505 (class 2606 OID 26419)
-- Name: function_entity_config_code function_entity_config_code_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_config_code
    ADD CONSTRAINT function_entity_config_code_pkey PRIMARY KEY (config_name, function_entity_id);


--
-- TOC entry 3507 (class 2606 OID 26426)
-- Name: function_entity_db_code function_entity_db_code_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_db_code
    ADD CONSTRAINT function_entity_db_code_pkey PRIMARY KEY (code_name, function_entity_id);


--
-- TOC entry 3509 (class 2606 OID 26438)
-- Name: function_entity_dependency_sources function_entity_dependency_sources_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_dependency_sources
    ADD CONSTRAINT function_entity_dependency_sources_pkey PRIMARY KEY (dependency_name, function_entity_id);


--
-- TOC entry 3511 (class 2606 OID 26450)
-- Name: function_entity_global_vars function_entity_global_vars_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_global_vars
    ADD CONSTRAINT function_entity_global_vars_pkey PRIMARY KEY (function_entity_id, var_name);


--
-- TOC entry 3501 (class 2606 OID 26405)
-- Name: function_metrics function_metrics_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_metrics
    ADD CONSTRAINT function_metrics_pkey PRIMARY KEY (id);


--
-- TOC entry 3513 (class 2606 OID 26462)
-- Name: functions functions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.functions
    ADD CONSTRAINT functions_pkey PRIMARY KEY (id);


--
-- TOC entry 3515 (class 2606 OID 26464)
-- Name: functions uk_function_name_user; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.functions
    ADD CONSTRAINT uk_function_name_user UNIQUE (name, user_id);


--
-- TOC entry 3517 (class 2606 OID 26478)
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- TOC entry 3519 (class 2606 OID 26476)
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- TOC entry 3521 (class 2606 OID 26480)
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- TOC entry 3525 (class 2606 OID 26496)
-- Name: function_entity_dependencies fkbt0glxa2ndadducgfqh4g1bkj; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_dependencies
    ADD CONSTRAINT fkbt0glxa2ndadducgfqh4g1bkj FOREIGN KEY (function_entity_id) REFERENCES public.functions(id);


--
-- TOC entry 3529 (class 2606 OID 26516)
-- Name: function_entity_methods fkbxl7p2iay4nhwhf0j18n6x0qq; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_methods
    ADD CONSTRAINT fkbxl7p2iay4nhwhf0j18n6x0qq FOREIGN KEY (function_entity_id) REFERENCES public.functions(id);


--
-- TOC entry 3522 (class 2606 OID 26481)
-- Name: function_entity_classes fkgcucqubp6riw98t7q7hevtf8s; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_classes
    ADD CONSTRAINT fkgcucqubp6riw98t7q7hevtf8s FOREIGN KEY (function_entity_id) REFERENCES public.functions(id);


--
-- TOC entry 3530 (class 2606 OID 26521)
-- Name: user_roles fkhfh9dx7w3ubf1co1vdev94g3f; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- TOC entry 3527 (class 2606 OID 26506)
-- Name: function_entity_env_vars fklwkgrn4yfymh8k257n91df7r3; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_env_vars
    ADD CONSTRAINT fklwkgrn4yfymh8k257n91df7r3 FOREIGN KEY (function_entity_id) REFERENCES public.functions(id);


--
-- TOC entry 3524 (class 2606 OID 26491)
-- Name: function_entity_db_code fkn5x1xce3kk2d2jrdl6uox6ynr; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_db_code
    ADD CONSTRAINT fkn5x1xce3kk2d2jrdl6uox6ynr FOREIGN KEY (function_entity_id) REFERENCES public.functions(id);


--
-- TOC entry 3523 (class 2606 OID 26486)
-- Name: function_entity_config_code fko57i2djgxnexwjfcrw0pbb7hu; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_config_code
    ADD CONSTRAINT fko57i2djgxnexwjfcrw0pbb7hu FOREIGN KEY (function_entity_id) REFERENCES public.functions(id);


--
-- TOC entry 3528 (class 2606 OID 26511)
-- Name: function_entity_global_vars fkq0mhctr5d0trvosrf2cl348mh; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_global_vars
    ADD CONSTRAINT fkq0mhctr5d0trvosrf2cl348mh FOREIGN KEY (function_entity_id) REFERENCES public.functions(id);


--
-- TOC entry 3526 (class 2606 OID 26501)
-- Name: function_entity_dependency_sources fkq68614bu6hp37y7gass8p7acx; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.function_entity_dependency_sources
    ADD CONSTRAINT fkq68614bu6hp37y7gass8p7acx FOREIGN KEY (function_entity_id) REFERENCES public.functions(id);


-- Completed on 2025-07-02 00:50:13 EEST

--
-- PostgreSQL database dump complete
--

