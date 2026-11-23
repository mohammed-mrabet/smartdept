variable "subscription_id" {
  description = "Identifiant de l'abonnement Azure (az account show --query id -o tsv)"
  type        = string
}

variable "location" {
  description = "Région Azure (francecentral est la plus proche du Maroc avec Azure for Students)"
  type        = string
  default     = "francecentral"
}

variable "prefix" {
  description = "Préfixe des ressources"
  type        = string
  default     = "smartdept"
}

variable "vm_size" {
  description = "Taille de la machine (B2s : 2 vCPU, 4 Go, suffisant pour la démonstration)"
  type        = string
  default     = "Standard_B2s"
}

variable "admin_username" {
  type    = string
  default = "azureuser"
}

variable "ssh_public_key_path" {
  description = "Clé publique SSH utilisée par Ansible"
  type        = string
  default     = "~/.ssh/id_rsa.pub"
}

variable "admin_cidr" {
  description = "Adresse autorisée en SSH, Grafana et Kibana (votre IP publique /32)"
  type        = string
  default     = "0.0.0.0/0"
}

variable "tags" {
  type = map(string)
  default = {
    projet = "smartdept"
    module = "genie-logiciel"
  }
}
