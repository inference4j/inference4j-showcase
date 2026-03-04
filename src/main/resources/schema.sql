
/*******************************************************************************
   Chinook Database - Version 1.4.5 (H2 Compatible)
   Adapted from: Chinook_Sqlite_AutoIncrementPKs.sql
   Description: Creates and populates the Chinook database for H2.
   Original Author: Luis Rocha
   License: https://github.com/lerocha/chinook-database/blob/master/LICENSE.md
********************************************************************************/

/*******************************************************************************
   Drop Tables (reverse dependency order)
********************************************************************************/
DROP TABLE IF EXISTS PlaylistTrack;
DROP TABLE IF EXISTS InvoiceLine;
DROP TABLE IF EXISTS Track;
DROP TABLE IF EXISTS Invoice;
DROP TABLE IF EXISTS Customer;
DROP TABLE IF EXISTS Employee;
DROP TABLE IF EXISTS Album;
DROP TABLE IF EXISTS Playlist;
DROP TABLE IF EXISTS MediaType;
DROP TABLE IF EXISTS Genre;
DROP TABLE IF EXISTS Artist;

/*******************************************************************************
   Create Tables (dependency order)
********************************************************************************/
CREATE TABLE Artist
(
    ArtistId INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    Name VARCHAR(120)
);

CREATE TABLE Genre
(
    GenreId INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    Name VARCHAR(120)
);

CREATE TABLE MediaType
(
    MediaTypeId INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    Name VARCHAR(120)
);

CREATE TABLE Playlist
(
    PlaylistId INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    Name VARCHAR(120)
);

CREATE TABLE Employee
(
    EmployeeId INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    LastName VARCHAR(20)  NOT NULL,
    FirstName VARCHAR(20)  NOT NULL,
    Title VARCHAR(30),
    ReportsTo INTEGER,
    BirthDate TIMESTAMP,
    HireDate TIMESTAMP,
    Address VARCHAR(70),
    City VARCHAR(40),
    State VARCHAR(40),
    Country VARCHAR(40),
    PostalCode VARCHAR(10),
    Phone VARCHAR(24),
    Fax VARCHAR(24),
    Email VARCHAR(60),
    FOREIGN KEY (ReportsTo) REFERENCES Employee (EmployeeId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE TABLE Album
(
    AlbumId INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    Title VARCHAR(160)  NOT NULL,
    ArtistId INTEGER  NOT NULL,
    FOREIGN KEY (ArtistId) REFERENCES Artist (ArtistId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE TABLE Customer
(
    CustomerId INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    FirstName VARCHAR(40)  NOT NULL,
    LastName VARCHAR(20)  NOT NULL,
    Company VARCHAR(80),
    Address VARCHAR(70),
    City VARCHAR(40),
    State VARCHAR(40),
    Country VARCHAR(40),
    PostalCode VARCHAR(10),
    Phone VARCHAR(24),
    Fax VARCHAR(24),
    Email VARCHAR(60)  NOT NULL,
    SupportRepId INTEGER,
    FOREIGN KEY (SupportRepId) REFERENCES Employee (EmployeeId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE TABLE Invoice
(
    InvoiceId INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    CustomerId INTEGER  NOT NULL,
    InvoiceDate TIMESTAMP  NOT NULL,
    BillingAddress VARCHAR(70),
    BillingCity VARCHAR(40),
    BillingState VARCHAR(40),
    BillingCountry VARCHAR(40),
    BillingPostalCode VARCHAR(10),
    Total NUMERIC(10,2)  NOT NULL,
    FOREIGN KEY (CustomerId) REFERENCES Customer (CustomerId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE TABLE Track
(
    TrackId INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    Name VARCHAR(200)  NOT NULL,
    AlbumId INTEGER,
    MediaTypeId INTEGER  NOT NULL,
    GenreId INTEGER,
    Composer VARCHAR(220),
    Milliseconds INTEGER  NOT NULL,
    Bytes INTEGER,
    UnitPrice NUMERIC(10,2)  NOT NULL,
    FOREIGN KEY (AlbumId) REFERENCES Album (AlbumId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION,
    FOREIGN KEY (GenreId) REFERENCES Genre (GenreId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION,
    FOREIGN KEY (MediaTypeId) REFERENCES MediaType (MediaTypeId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE TABLE InvoiceLine
(
    InvoiceLineId INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    InvoiceId INTEGER  NOT NULL,
    TrackId INTEGER  NOT NULL,
    UnitPrice NUMERIC(10,2)  NOT NULL,
    Quantity INTEGER  NOT NULL,
    FOREIGN KEY (InvoiceId) REFERENCES Invoice (InvoiceId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION,
    FOREIGN KEY (TrackId) REFERENCES Track (TrackId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE TABLE PlaylistTrack
(
    PlaylistId INTEGER  NOT NULL,
    TrackId INTEGER  NOT NULL,
    CONSTRAINT PK_PlaylistTrack PRIMARY KEY  (PlaylistId, TrackId),
    FOREIGN KEY (PlaylistId) REFERENCES Playlist (PlaylistId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION,
    FOREIGN KEY (TrackId) REFERENCES Track (TrackId) 
		ON DELETE NO ACTION ON UPDATE NO ACTION
);


/*******************************************************************************
   Create Indexes
********************************************************************************/
CREATE INDEX IFK_AlbumArtistId ON Album (ArtistId);
CREATE INDEX IFK_CustomerSupportRepId ON Customer (SupportRepId);
CREATE INDEX IFK_EmployeeReportsTo ON Employee (ReportsTo);
CREATE INDEX IFK_InvoiceCustomerId ON Invoice (CustomerId);
CREATE INDEX IFK_InvoiceLineInvoiceId ON InvoiceLine (InvoiceId);
CREATE INDEX IFK_InvoiceLineTrackId ON InvoiceLine (TrackId);
CREATE INDEX IFK_PlaylistTrackPlaylistId ON PlaylistTrack (PlaylistId);
CREATE INDEX IFK_PlaylistTrackTrackId ON PlaylistTrack (TrackId);
CREATE INDEX IFK_TrackAlbumId ON Track (AlbumId);
CREATE INDEX IFK_TrackGenreId ON Track (GenreId);
CREATE INDEX IFK_TrackMediaTypeId ON Track (MediaTypeId);

