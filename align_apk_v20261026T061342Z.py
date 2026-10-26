import sys,zipfile,struct
ALIGNER_VERSION="align_apk_v20261026T061342Z"
source,target=sys.argv[1:3]
with zipfile.ZipFile(source,"r") as old,zipfile.ZipFile(target,"w",allowZip64=True) as new:
    for info in old.infolist():
        if info.is_dir():continue
        record=zipfile.ZipInfo(info.filename,info.date_time)
        record.compress_type=info.compress_type
        record.external_attr=info.external_attr
        record.create_system=info.create_system
        record.extra=info.extra
        record.comment=info.comment
        if record.compress_type==zipfile.ZIP_STORED:
            offset=new.fp.tell()+30+len(record.filename.encode("utf-8"))+len(record.extra)
            pad=(4-(offset+4)%4)%4+4
            record.extra+=struct.pack("<HH",65535,pad-4)+bytes(pad-4)
        new.writestr(record,old.read(info))
