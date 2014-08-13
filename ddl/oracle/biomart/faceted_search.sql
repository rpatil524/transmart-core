--
-- Type: TABLE; Owner: BIOMART; Name: FACETED_SEARCH
--
 CREATE TABLE "BIOMART"."FACETED_SEARCH" 
  (	"ANALYSIS_ID" NUMBER(18,0), 
"STUDY" NUMBER(18,0), 
"STUDY_ID" NUMBER(18,0), 
"DISEASE" NVARCHAR2(510), 
"ANALYSES" NVARCHAR2(200), 
"DATA_TYPE" VARCHAR2(50 BYTE), 
"PLATFORM" VARCHAR2(20 BYTE), 
"OBSERVATION" NVARCHAR2(200), 
"FACET_ID" NUMBER(10,0), 
 PRIMARY KEY ("FACET_ID")
 USING INDEX
 TABLESPACE "TRANSMART"  ENABLE
  ) SEGMENT CREATION IMMEDIATE
NOCOMPRESS NOLOGGING
 TABLESPACE "TRANSMART" ;
